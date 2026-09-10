package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.UrgePauseDtos.*;
import com.jllado.weightcontrol.api.dto.CoachDtos;
import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import com.jllado.weightcontrol.domain.UrgePause;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UrgePauseRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class UrgePauseService {
    private final UrgePauseRepository repository;
    private final UserRepository users;
    private final InAppNotificationService notifications;
    private final PersonalRecordMutationService mutations;
    private final ApplicationEventPublisher events;

    public UrgePauseService(UrgePauseRepository repository, UserRepository users, InAppNotificationService notifications,
                            PersonalRecordMutationService mutations, ApplicationEventPublisher events) {
        this.repository = repository;
        this.users = users;
        this.notifications = notifications;
        this.mutations = mutations;
        this.events = events;
    }

    public CurrentResponse current(User user) {
        return new CurrentResponse(repository.findByUserAndStatus(user, UrgePause.Status.ACTIVE).map(PauseResponse::from).orElse(null), now());
    }

    public CurrentResponse start(User user, StartRequest request) {
        lock(user);
        if (repository.findByUserAndStatus(user, UrgePause.Status.ACTIVE).isEmpty()) {
            create(user, UUID.randomUUID().toString(), StringUtils.hasText(request.description()) ? request.description().strip() : null);
        }
        return current(user);
    }

    public CurrentResponse checkIn(User user, Long id, CheckInRequest request) {
        lock(user);
        UrgePause pause = owned(user, id);
        requireReady(pause);
        pause.setAnswer(request.answer());
        pause.setAnsweredAt(now());
        return current(user);
    }

    public CurrentResponse cancel(User user, Long id) {
        lock(user);
        UrgePause pause = owned(user, id);
        if (pause.getStatus() == UrgePause.Status.ACTIVE) close(pause, UrgePause.Status.CANCELLED);
        return current(user);
    }

    public CurrentResponse repeat(User user, Long id) {
        lock(user);
        UrgePause pause = owned(user, id);
        if (pause.getStatus() == UrgePause.Status.REPEATED) return current(user);
        requireReady(pause);
        close(pause, UrgePause.Status.REPEATED);
        repository.flush();
        create(user, pause.getSessionKey(), pause.getDescription());
        return current(user);
    }

    public RecordMutationResponse<DecisionOutcomeResponse> finish(User user, Long id, FinishRequest request) {
        lock(user);
        UrgePause pause = owned(user, id);
        if (pause.getStatus() == UrgePause.Status.FINISHED) {
            return new RecordMutationResponse<>(pause.getDecisionOutcome() == null ? null : DecisionOutcomeResponse.from(pause.getDecisionOutcome()), List.of());
        }
        requireReady(pause);
        RecordMutationResponse<DecisionOutcomeResponse> response = new RecordMutationResponse<>(null, List.of());
        if (request.outcome() != null) {
            var result = mutations.createDecisionOutcome(user, new DecisionOutcomeRequest(now().toLocalDate(), request.outcome(), request.reason()));
            pause.setDecisionOutcome(result.result());
            response = new RecordMutationResponse<>(DecisionOutcomeResponse.from(result.result()), result.achievements());
        }
        close(pause, UrgePause.Status.FINISHED);
        return response;
    }

    public void notifyDue(Long id, OffsetDateTime now) {
        // Read ownership first, then reload after the same user lock used by lifecycle actions.
        UrgePause candidate = repository.findById(id).orElseThrow();
        lock(candidate.getUser());
        // Refresh prevents a pre-lock read from retaining a concurrently cancelled interval.
        entityManager.refresh(candidate, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (candidate.getStatus() != UrgePause.Status.ACTIVE || candidate.getNotifiedAt() != null || candidate.getEndsAt().isAfter(now)) return;
        candidate.setNotifiedAt(now);
        var notification = notifications.recordUrgePause(candidate, now);
        events.publishEvent(new PauseDue(candidate.getUser().getId(), notification.getTitle(), notification.getMessage(), notification.getActionUrl(), notification.getDeduplicationKey()));
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public record PauseDue(Long userId, String title, String message, String actionUrl, String key) { }

    public CoachDtos.DomainAvailability availability(User user) {
        return new CoachDtos.DomainAvailability(com.jllado.weightcontrol.domain.CoachDomain.BEHAVIOR, repository.countByUser(user),
            repository.findFirstByUserOrderByStartedAtAsc(user).map(p -> DateTimes.toLocalDate(p.getStartedAt())).orElse(null),
            repository.findFirstByUserOrderByStartedAtDesc(user).map(p -> DateTimes.toLocalDate(p.getStartedAt())).orElse(null));
    }

    public List<CoachDtos.PauseSessionData> context(User user, LocalDate from, LocalDate to) {
        return repository.findOverlapping(user, DateTimes.startOfDay(from), DateTimes.startOfDay(to.plusDays(1))).stream()
            .collect(Collectors.groupingBy(UrgePause::getSessionKey, LinkedHashMap::new, Collectors.toList())).values().stream()
            .map(intervals -> new CoachDtos.PauseSessionData(intervals.getFirst().getDescription(), intervals.stream()
                .map(p -> new CoachDtos.PauseIntervalData(p.getStartedAt(), p.getEndsAt(), p.getClosedAt(), p.getStatus(), p.getAnswer(), p.getAnsweredAt(),
                    p.getDecisionOutcome() == null ? null : p.getDecisionOutcome().getOutcome())).toList())).toList();
    }

    private void create(User user, String sessionKey, String description) {
        UrgePause pause = new UrgePause();
        pause.setUser(user);
        pause.setSessionKey(sessionKey);
        pause.setDescription(description);
        pause.setStartedAt(now());
        pause.setEndsAt(pause.getStartedAt().plusMinutes(15));
        repository.save(pause);
    }

    private void close(UrgePause pause, UrgePause.Status status) {
        pause.setStatus(status);
        pause.setClosedAt(now());
        notifications.completeUrgePause(pause);
    }

    private void requireReady(UrgePause pause) {
        if (pause.getStatus() != UrgePause.Status.ACTIVE || pause.getEndsAt().isAfter(now())) {
            throw new BadRequestException("This pause is not ready for a check-in. Refresh to see the current pause.");
        }
    }

    private UrgePause owned(User user, Long id) {
        return repository.findByIdAndUser(id, user).orElseThrow(() -> new NotFoundException("Pause not found"));
    }

    private void lock(User user) { users.findByIdForUpdate(user.getId()).orElseThrow(); }
    private OffsetDateTime now() { return OffsetDateTime.now(DateTimes.USER_ZONE); }
}
