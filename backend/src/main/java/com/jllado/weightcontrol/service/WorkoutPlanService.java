package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.domain.WorkoutPlanDay.*;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WorkoutPlanRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WorkoutPlanService {
    private final WorkoutPlanRepository repository;
    private final UserRepository users;
    private final ExerciseService exercises;
    public WorkoutPlanService(WorkoutPlanRepository repository, UserRepository users, ExerciseService exercises) {
        this.repository = repository; this.users = users; this.exercises = exercises;
    }
    public Optional<WorkoutPlanResponse> current(User user) { return repository.findByUserAndArchivedAtIsNull(user).map(WorkoutPlanResponse::from); }
    public WorkoutPlanResponse get(User user, Long id) { return WorkoutPlanResponse.from(requireOwned(user, id)); }
    public WorkoutPlanArchiveResponse archive(User user, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("Archive page must be non-negative and size must be between 1 and 100");
        var result = repository.findByUserAndArchivedAtIsNotNullOrderByArchivedAtDescIdDesc(user, PageRequest.of(page, size));
        return new WorkoutPlanArchiveResponse(result.map(WorkoutPlanSummary::from).getContent(), page, result.getTotalElements(), result.getTotalPages());
    }
    public WorkoutPlanResponse create(User user, WorkoutPlanRequest request) {
        var owner = users.findByIdForUpdate(user.getId()).orElseThrow();
        var previous = repository.findByUserAndArchivedAtIsNull(owner);
        var days = snapshot(request, previous.map(WorkoutPlan::getDays).orElse(List.of()));
        previous.ifPresent(plan -> { plan.setArchivedAt(Instant.now()); repository.saveAndFlush(plan); });
        var plan = new WorkoutPlan();
        plan.setUser(owner); plan.setCreatedAt(Instant.now());
        apply(plan, request, days);
        return WorkoutPlanResponse.from(repository.saveAndFlush(plan));
    }
    public WorkoutPlanResponse update(User user, Long id, WorkoutPlanUpdateRequest request) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
        var plan = requireOwned(user, id);
        checkEditable(plan, request.updateToken());
        apply(plan, request.plan(), snapshot(request.plan(), plan.getDays()));
        return WorkoutPlanResponse.from(repository.saveAndFlush(plan));
    }
    public WorkoutPlanEditContext editContext(User user) {
        return new WorkoutPlanEditContext(current(user).orElse(null), exercises.findAll().stream().map(WorkoutPlanExerciseChoice::from).toList());
    }
    public WorkoutPlanResponse updateConfirmed(User user, CoachWorkoutPlanUpdateRequest request) {
        if (!Boolean.TRUE.equals(request.confirmed())) throw new BadRequestException("Explicit confirmation is required");
        if (!Integer.valueOf(1).equals(request.saunaSchemaVersion())) throw new BadRequestException("Update the Coach sauna schema before replacing a workout plan");
        users.findByIdForUpdate(user.getId()).orElseThrow();
        var current = repository.findByUserAndArchivedAtIsNull(user);
        if (current.isEmpty()) {
            if (request.updateToken() != null && !request.updateToken().isBlank()) throw new BadRequestException("Do not send an update token when creating a workout plan");
            var plan = new WorkoutPlan();
            plan.setUser(user);
            plan.setCreatedAt(Instant.now());
            apply(plan, request.plan(), snapshot(request.plan(), List.of()));
            return WorkoutPlanResponse.from(repository.saveAndFlush(plan));
        }
        var plan = current.orElseThrow();
        checkEditable(plan, request.updateToken());
        apply(plan, request.plan(), snapshot(request.plan(), plan.getDays()));
        return WorkoutPlanResponse.from(repository.saveAndFlush(plan));
    }
    private WorkoutPlan requireOwned(User user, Long id) { return repository.findByIdAndUser(id, user).orElseThrow(() -> new NotFoundException("Workout plan not found")); }
    private void checkEditable(WorkoutPlan plan, String token) {
        if (plan.getArchivedAt() != null) throw new BadRequestException("Archived workout plans cannot be edited");
        if (!plan.getUpdateToken().equals(token)) throw new ResponseStatusException(HttpStatus.CONFLICT, "The workout plan changed. Reload it and review your changes before saving again.");
    }
    private void apply(WorkoutPlan plan, WorkoutPlanRequest request, List<WorkoutPlanDay> days) {
        plan.setStartDate(request.startDate()); plan.setReviewDate(request.reviewDate()); plan.setNotes(request.notes());
        plan.setDays(days); plan.setUpdatedAt(Instant.now()); plan.setUpdateToken(UUID.randomUUID().toString());
    }
    private List<WorkoutPlanDay> snapshot(WorkoutPlanRequest request, List<WorkoutPlanDay> previous) {
        if (request.reviewDate().isBefore(request.startDate())) throw new BadRequestException("Review date must not be before the start date");
        var weekdays = EnumSet.noneOf(DayOfWeek.class);
        for (var day : request.days()) {
            if (!weekdays.add(day.day())) throw new BadRequestException("Each weekday must appear once");
            var sessions = sessions(day);
            if (day.rest() != sessions.isEmpty()) throw new BadRequestException("Rest days must have no sessions; workout days require at least one session");
            for (var session : sessions) {
                boolean sauna = Boolean.TRUE.equals(session.saunaSession());
                WorkoutSauna.totalMinutes(sauna, session.saunaRoundsMinutes());
                if (session.lines().isEmpty() && !sauna) throw new BadRequestException("Each workout session requires exercises or sauna rounds");
            }
            for (var session : sessions) WorkoutSupersets.validate(session.lines().stream().map(WorkoutPlanLineRequest::supersetGroupId).toList(), session.lines().stream().map(line -> line.segments().size()).toList());
        }
        if (weekdays.size() != 7) throw new BadRequestException("Include all seven weekdays");
        Map<Long, Target> saved = new HashMap<>();
        previous.forEach(day -> day.sessions().forEach(session -> session.lines().forEach(line -> saved.putIfAbsent(line.exerciseId(), line))));
        return request.days().stream().sorted(Comparator.comparing(WorkoutPlanDayRequest::day)).map(day -> {
            var sessions = sessions(day).stream().map(session -> {
                Set<Long> used = new HashSet<>();
                var lines = session.lines().stream().map(line -> {
                    if (!used.add(line.exerciseId())) throw new BadRequestException("An exercise cannot be repeated in the same session");
                    var old = saved.get(line.exerciseId());
                    Exercise exercise;
                    if (old == null) exercise = exercises.require(line.exerciseId());
                    else {
                        exercise = new Exercise(); exercise.setId(old.exerciseId()); exercise.setName(old.exerciseName()); exercise.setDescription(old.exerciseDescription());
                        exercise.setTrackingMode(old.trackingMode()); exercise.setExerciseType(old.exerciseType());
                        exercise.setCardioMetric(old.cardioMetric());
                    }
                    try {
                        WorkoutTargets.validatePlan(exercise, line.stretchingUnit(), line.segments());
                    } catch (BadRequestException exception) {
                        throw new BadRequestException(day.day() + " — " + exercise.getName() + ": " + exception.getMessage());
                    }
                    var segments = line.segments().stream().map(segment -> new Segment(segment.repetitions(), segment.durationSeconds(), scale(segment.weight()), scale(segment.speedKph()), scale(segment.cadenceRpm()), scale(segment.distanceKm()), scale(segment.inclinePercent()), segment.resistanceLevel(), segment.breaths())).toList();
                    return new Target(exercise.getId(), exercise.getName(), exercise.getDescription(), exercise.getTrackingMode(), exercise.getExerciseType(), exercise.getCardioMetric(), segments, line.stretchingUnit(), line.supersetGroupId());
                }).toList();
                return new WorkoutPlanDay.Session(blankToNull(session.name()), blankToNull(session.note()), lines,
                    Boolean.TRUE.equals(session.saunaSession()), session.saunaRoundsMinutes() == null ? List.of() : session.saunaRoundsMinutes());
            }).toList();
            return new WorkoutPlanDay(day.day(), day.rest(), sessions.isEmpty() ? blankToNull(day.note()) : null, sessions);
        }).toList();
    }
    private List<WorkoutPlanSessionRequest> sessions(WorkoutPlanDayRequest day) {
        if (day.sessions() != null) return day.sessions();
        var lines = day.lines() == null ? List.<WorkoutPlanLineRequest>of() : day.lines();
        return lines.isEmpty() ? List.of() : List.of(new WorkoutPlanSessionRequest(null, day.note(), lines));
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private BigDecimal scale(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }
}
