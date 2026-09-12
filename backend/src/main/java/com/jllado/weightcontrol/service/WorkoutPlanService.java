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
        users.findByIdForUpdate(user.getId()).orElseThrow();
        var plan = repository.findByUserAndArchivedAtIsNull(user).orElseThrow(() -> new NotFoundException("Create a workout plan in the app first"));
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
            if (day.rest() != day.lines().isEmpty()) throw new BadRequestException("Rest days must have no exercises; workout days require exercises");
        }
        if (weekdays.size() != 7) throw new BadRequestException("Include all seven weekdays");
        Map<Long, Target> saved = new HashMap<>();
        previous.forEach(day -> day.lines().forEach(line -> saved.putIfAbsent(line.exerciseId(), line)));
        return request.days().stream().sorted(Comparator.comparing(WorkoutPlanDayRequest::day)).map(day -> {
            Set<Long> used = new HashSet<>();
            var lines = day.lines().stream().map(line -> {
                if (!used.add(line.exerciseId())) throw new BadRequestException("An exercise cannot be repeated in the same day");
                var old = saved.get(line.exerciseId());
                Exercise exercise;
                if (old == null) exercise = exercises.require(line.exerciseId());
                else {
                    exercise = new Exercise(); exercise.setId(old.exerciseId()); exercise.setName(old.exerciseName()); exercise.setDescription(old.exerciseDescription());
                    exercise.setTrackingMode(old.trackingMode()); exercise.setExerciseType(old.exerciseType());
                }
                WorkoutTargets.validate(exercise, line.stretchingUnit(), line.segments());
                var segments = line.segments().stream().map(segment -> new Segment(segment.repetitions(), segment.durationSeconds(), scale(segment.weight()), scale(segment.speedKph()), scale(segment.distanceKm()), scale(segment.inclinePercent()), segment.resistanceLevel(), segment.breaths())).toList();
                return new Target(exercise.getId(), exercise.getName(), exercise.getDescription(), exercise.getTrackingMode(), exercise.getExerciseType(), segments, line.stretchingUnit());
            }).toList();
            return new WorkoutPlanDay(day.day(), day.rest(), day.note(), lines);
        }).toList();
    }
    private BigDecimal scale(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }
}
