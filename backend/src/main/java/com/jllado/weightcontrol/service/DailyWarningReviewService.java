package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.CoachDtos.CreateWarningRequest;
import com.jllado.weightcontrol.api.dto.CoachDtos.WarningContent;
import com.jllado.weightcontrol.api.dto.CoachDtos.WarningResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ExistingWarningReview;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.NewWarningReview;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.WarningAssessment;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.WarningReviewEnvelope;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.CoachWarningRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class DailyWarningReviewService {
    private final EntityManager entityManager;
    private final CoachWarningRepository repository;
    private final CoachWarningService warnings;
    private final Clock clock;
    private final WarningMetricEvaluator metricEvaluator;

    @Autowired
    public DailyWarningReviewService(EntityManager entityManager, CoachWarningRepository repository, CoachWarningService warnings, WarningMetricEvaluator metricEvaluator) {
        this(entityManager, repository, warnings, Clock.systemUTC(), metricEvaluator);
    }

    DailyWarningReviewService(EntityManager entityManager, CoachWarningRepository repository, CoachWarningService warnings, Clock clock, WarningMetricEvaluator metricEvaluator) {
        this.entityManager = entityManager;
        this.repository = repository;
        this.warnings = warnings;
        this.clock = clock;
        this.metricEvaluator = metricEvaluator;
    }

    public WarningReviewSnapshot review(User user, WarningReviewEnvelope request) {
        entityManager.find(User.class, user.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (!request.reviewedDate().equals(LocalDate.now(clock.withZone(DateTimes.USER_ZONE)))) {
            throw new BadRequestException("Warning review date must be today's date in Europe/Madrid");
        }

        Map<Long, CoachWarning> active = new HashMap<>();
        for (CoachWarning warning : repository.findByUserAndStatusOrderByTypeAsc(user, CoachWarningStatus.ACTIVE)) {
            active.put(warning.getId(), warning);
        }
        Set<Long> reviewed = new HashSet<>();
        for (ExistingWarningReview entry : request.reviews()) {
            WarningResponse current = warnings.current(user, entry.id());
            if (!reviewed.add(entry.id())) throw new BadRequestException("Each active warning needs exactly one review");
            CoachWarning warning = active.get(entry.id());
            if (warning == null) throw conflict("Warning set changed; reload current warnings and reassess");
            if (warning.getVersion() != entry.version()) throw conflict("Warning changed; reload current warnings and reassess");
            WarningProgress previous = current.progress();
            validateAssessment(entry.assessment(), request.reviewedDate(), previous);
        }
        if (!reviewed.equals(active.keySet())) throw conflict("Active warnings changed or a review is missing; reload and reassess every current warning");

        Set<CoachWarningType> newTypes = new HashSet<>();
        Set<java.util.UUID> newKeys = new HashSet<>();
        for (NewWarningReview entry : request.newWarnings()) {
            if (!newTypes.add(entry.type()) || !newKeys.add(entry.requestKey())) throw new BadRequestException("New warnings must have distinct types and request keys");
            if (repository.findByUserAndRequestKey(user, entry.requestKey().toString()).isPresent()) {
                throw conflict("New warning request was already used; read back the reflection and current warnings before retrying");
            }
            boolean sameTypeRemainsActive = active.values().stream().anyMatch(warning -> warning.getType() == entry.type()
                && request.reviews().stream().noneMatch(review -> review.id().equals(warning.getId())
                    && metricEvaluator.evaluate(review.assessment().metricsReview(), request.reviewedDate(), null) == WarningGoalStatus.MET));
            if (sameTypeRemainsActive) throw conflict("This warning type is already active; reassess the current warning");
            if (metricEvaluator.evaluate(entry.assessment().metricsReview(), request.reviewedDate(), null) == WarningGoalStatus.MET) throw new BadRequestException("A newly detected warning cannot already be resolved");
            if (entry.onsetDate() != null && entry.onsetDate().isAfter(request.reviewedDate())) throw new BadRequestException("Warning onset must not follow its review date");
            validateAssessment(entry.assessment(), request.reviewedDate(), null);
        }

        List<WarningReviewItem> items = new ArrayList<>();
        for (ExistingWarningReview entry : request.reviews()) {
            CoachWarning warning = active.get(entry.id());
            WarningProgress progress = progress(entry.assessment(), request.reviewedDate(), entry.version() + 1, false);
            WarningContent content = new WarningContent(progress.assessment(), progress.evidence(), progress.nextAction(), warning.getOnsetDate(), request.reviewedDate(), progress.metricsReview());
            items.add(item(warnings.reviewDaily(user, entry.id(), entry.version(), content, progress)));
        }
        for (NewWarningReview entry : request.newWarnings()) {
            WarningProgress progress = progress(entry.assessment(), request.reviewedDate(), 0, true);
            WarningContent content = new WarningContent(progress.assessment(), progress.evidence(), progress.nextAction(), entry.onsetDate(), request.reviewedDate(), progress.metricsReview());
            WarningResponse created = warnings.create(user, new CreateWarningRequest(entry.requestKey(), entry.type(), content), progress);
            items.add(item(created));
        }
        items.sort(Comparator.comparing(WarningReviewItem::type).thenComparing(WarningReviewItem::id));
        return new WarningReviewSnapshot(request.reviewedDate(), List.copyOf(items));
    }

    private void validateAssessment(WarningAssessment assessment, LocalDate reviewedDate, WarningProgress previous) {
        metricEvaluator.evaluate(assessment.metricsReview(), reviewedDate, previous == null ? null : previous.metricsReview());
        var metrics = assessment.metricsReview();
        if (!assessment.recentEnd().equals(metrics.windowEnd())
            || !assessment.recentStart().equals(metrics.windowEnd().minusDays(metrics.windowDays() - 1)))
            throw new BadRequestException("Warning evidence dates must match the rolling metric window");
    }

    private WarningProgress progress(WarningAssessment assessment, LocalDate reviewedDate, long version, boolean initialReview) {
        var metrics = assessment.metricsReview();
        var status = metricEvaluator.evaluate(metrics, reviewedDate, null);
        var direction = status == WarningGoalStatus.MET ? WarningDirection.RESOLVED
            : status == WarningGoalStatus.UNKNOWN ? WarningDirection.INSUFFICIENT_EVIDENCE : WarningDirection.UNCHANGED;
        return new WarningProgress(direction, assessment.assessment(), assessment.evidence(), assessment.nextAction(),
            assessment.resolutionCriteria(), assessment.criteriaProgress(), assessment.recentStart(), assessment.recentEnd(),
            null, null, assessment.criteriaChangeReason(), status == WarningGoalStatus.MET ? metrics.shortStatus() : null,
            reviewedDate, version, initialReview, metrics, status, java.time.Instant.now());
    }

    private WarningReviewItem item(WarningResponse response) {
        return new WarningReviewItem(response.id(), response.type(), response.status(), response.version(), response.progress(), response.createdAt());
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
