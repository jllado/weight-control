package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.ActionDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.*;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service @Transactional
public class ActionService {
    private final ActionAttemptRepository attempts;
    private final UserRepository users;
    private final ActionRecommendationService recommendations;

    public ActionService(ActionAttemptRepository attempts, UserRepository users, ActionRecommendationService recommendations) {
        this.attempts = attempts;
        this.users = users;
        this.recommendations = recommendations;
    }

    public List<ActionResponse> list(User user, LocalDate date, String view) {
        validateDate(date);
        if (!view.equals("ACTIVE") && !view.equals("HISTORY")) throw new BadRequestException("Choose Active or History");
        return attempts.findByUserOrderByStartDateAscIdAsc(user).stream().map(attempt -> response(attempt, date))
            .filter(row -> view.equals("HISTORY")
                ? row.status().equals("COMPLETED") || row.status().equals("STOPPED")
                : row.status().equals("ACTIVE") || row.status().equals("SCHEDULED")
                    || date.equals(row.completedDate()) && !row.status().equals("STOPPED"))
            .sorted(Comparator.comparing((ActionResponse row) -> row.status().equals("SCHEDULED"))
                .thenComparing(ActionResponse::startDate).thenComparing(ActionResponse::id)).toList();
    }

    public ActionResponse find(User user, Long id, LocalDate date) {
        validateDate(date);
        return response(owned(user, id), date);
    }

    public ActionResponse create(User user, ActionRequest request) {
        lock(user);
        var source = request.sourceKey() == null ? null : recommendations.resolve(user, request.sourceKey());
        if (source != null && source.existingAttemptId() != null) return find(user, source.existingAttemptId(), today());
        ActionAttempt attempt = new ActionAttempt();
        attempt.setUser(user);
        apply(attempt, request);
        if (source != null) {
            attempt.setSourceKey(source.sourceKey());
            attempt.setSourceLabel(source.sourceLabel());
            attempt.setRecommendationFingerprint(WeeklyRecommendationMapper.fingerprint(source.wording()));
        }
        return response(attempts.saveAndFlush(attempt), today());
    }

    public ActionResponse update(User user, Long id, ActionRequest request) {
        lock(user);
        ActionAttempt attempt = owned(user, id);
        if (attempt.getFirstCheckinAt() != null &&
            (!attempt.getStartDate().equals(request.startDate()) || attempt.getTargetDays() != request.targetDays())) {
            throw new BadRequestException("Start date and target days are locked after the first check-in");
        }
        apply(attempt, request);
        return response(attempts.save(attempt), today());
    }

    public ActionResponse stop(User user, Long id) {
        lock(user);
        ActionAttempt attempt = owned(user, id);
        if (progress(attempt, today()).status().equals("COMPLETED")) throw new BadRequestException("This action is already completed");
        if (attempt.getStoppedDate() == null) attempt.setStoppedDate(today());
        return response(attempts.save(attempt), today());
    }

    public ActionResponse restart(User user, Long id, ActionRequest request) {
        lock(user);
        ActionAttempt previous = owned(user, id);
        String status = progress(previous, today()).status();
        if (!status.equals("COMPLETED") && !status.equals("STOPPED")) throw new BadRequestException("Stop or complete this attempt before starting again");
        ActionAttempt attempt = new ActionAttempt();
        attempt.setUser(user);
        apply(attempt, request);
        attempt.setSourceKey(previous.getSourceKey());
        attempt.setSourceLabel(previous.getSourceLabel());
        attempt.setRecommendationFingerprint(previous.getRecommendationFingerprint());
        attempt.setRestartedFromId(previous.getId());
        return response(attempts.saveAndFlush(attempt), today());
    }

    public ActionResponse check(User user, Long id, LocalDate date, boolean checked) {
        lock(user);
        validateDate(date);
        ActionAttempt attempt = owned(user, id);
        if (date.isBefore(attempt.getStartDate())) throw new BadRequestException("Check-ins cannot precede the start date");
        if (attempt.getStoppedDate() != null && date.isAfter(attempt.getStoppedDate())) throw new BadRequestException("Check-ins cannot follow the stop date");
        LocalDate completion = progress(attempt, today()).completedDate();
        if (checked && completion != null && date.isAfter(completion)) throw new BadRequestException("This attempt was already completed on " + completion);
        var existing = attempt.getCheckins().stream().filter(check -> check.getCheckinDate().equals(date)).findFirst();
        if (checked && existing.isEmpty()) {
            ActionCheckin check = new ActionCheckin();
            check.setAttempt(attempt);
            check.setCheckinDate(date);
            attempt.getCheckins().add(check);
            if (attempt.getFirstCheckinAt() == null) attempt.setFirstCheckinAt(Instant.now());
        } else if (!checked) existing.ifPresent(attempt.getCheckins()::remove);
        return response(attempts.saveAndFlush(attempt), today());
    }

    private void apply(ActionAttempt attempt, ActionRequest request) {
        attempt.setWording(request.wording().trim());
        attempt.setStartDate(request.startDate());
        attempt.setTargetDays(request.targetDays());
    }
    private void lock(User user) { users.findByIdForUpdate(user.getId()).orElseThrow(); }
    private ActionAttempt owned(User user, Long id) {
        return attempts.findByUserAndId(user, id).orElseThrow(() -> new NotFoundException("Action not found"));
    }
    private LocalDate today() { return LocalDate.now(DateTimes.USER_ZONE); }
    private void validateDate(LocalDate date) {
        if (date.isAfter(today())) throw new BadRequestException("Future check-ins and progress are unavailable");
    }
    private ActionProgress progress(ActionAttempt attempt, LocalDate date) {
        return ActionProgress.calculate(attempt.getStartDate(), attempt.getTargetDays(),
            attempt.getCheckins().stream().map(ActionCheckin::getCheckinDate).collect(Collectors.toSet()),
            attempt.getStoppedDate(), date, today());
    }
    private ActionResponse response(ActionAttempt attempt, LocalDate date) {
        var result = progress(attempt, date);
        return new ActionResponse(attempt.getId(), attempt.getWording(), attempt.getStartDate(), attempt.getTargetDays(),
            attempt.getFirstCheckinAt() != null, attempt.getStoppedDate(), attempt.getRestartedFromId(),
            attempt.getSourceKey(), attempt.getSourceLabel(), result.status(), result.streak(), result.bestStreak(),
            result.percentage(), result.completedDate(), result.checked(),
            attempt.getCheckins().stream().map(ActionCheckin::getCheckinDate).sorted().toList());
    }
}
