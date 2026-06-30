package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutLineRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutSegmentRequest;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class WorkoutService {

    private final WorkoutRepository repository;
    private final ExerciseService exerciseService;

    public WorkoutService(WorkoutRepository repository, ExerciseService exerciseService) {
        this.repository = repository;
        this.exerciseService = exerciseService;
    }

    public List<Workout> findAll(User user) {
        List<Workout> workouts = repository.findByUserOrderByWorkoutDateDesc(user);
        initializeLines(workouts);
        return workouts;
    }

    public Workout create(User user, WorkoutRequest request) {
        validateRequest(user, request, null);
        Workout workout = new Workout();
        workout.setUser(user);
        apply(workout, request);
        return repository.save(workout);
    }

    public Workout update(User user, Long id, WorkoutRequest request) {
        validateRequest(user, request, id);
        Workout workout = requireOwned(user, id);
        workout.setWorkoutDate(request.workoutDate());
        workout.setNote(blankToNull(request.note()));
        workout.getLines().clear();
        repository.flush();
        applyLines(workout, request);
        return repository.save(workout);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public Workout requireOwned(User user, Long id) {
        Workout workout = repository.findWithLinesById(id).orElseThrow(() -> new NotFoundException("Workout not found"));
        if (!workout.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Workout not found");
        }
        initializeLines(List.of(workout));
        return workout;
    }

    private void initializeLines(List<Workout> workouts) {
        for (Workout workout : workouts) {
            for (WorkoutLine line : workout.getLines()) {
                line.getSegments().size();
            }
        }
    }

    private void apply(Workout workout, WorkoutRequest request) {
        workout.setWorkoutDate(request.workoutDate());
        workout.setNote(blankToNull(request.note()));
        applyLines(workout, request);
    }

    private void applyLines(Workout workout, WorkoutRequest request) {
        for (int i = 0; i < request.lines().size(); i++) {
            WorkoutLineRequest lineRequest = request.lines().get(i);
            Exercise exercise = exerciseService.require(lineRequest.exerciseId());
            WorkoutLine line = new WorkoutLine();
            line.setWorkout(workout);
            line.setExercise(exercise);
            line.setPosition(i);
            line.setCalories(lineRequest.calories());
            for (int j = 0; j < lineRequest.segments().size(); j++) {
                WorkoutSegmentRequest segmentRequest = lineRequest.segments().get(j);
                WorkoutSegment segment = new WorkoutSegment();
                segment.setWorkoutLine(line);
                segment.setPosition(j);
                segment.setRepetitions(segmentRequest.repetitions());
                segment.setDurationSeconds(segmentRequest.durationSeconds());
                segment.setWeight(scale(segmentRequest.weight()));
                segment.setSpeedKph(scale(segmentRequest.speedKph()));
                segment.setInclinePercent(scale(segmentRequest.inclinePercent()));
                segment.setResistanceLevel(segmentRequest.resistanceLevel());
                line.getSegments().add(segment);
            }
            workout.getLines().add(line);
        }
    }

    private void validateRequest(User user, WorkoutRequest request, Long currentWorkoutId) {
        if (request.workoutDate().isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Workout date cannot be in the future");
        }
        repository.findByUserAndWorkoutDate(user, request.workoutDate())
            .filter(existing -> !existing.getId().equals(currentWorkoutId))
            .ifPresent(existing -> {
                throw new BadRequestException("Workout entry already exists for this date");
            });
        Set<Long> exerciseIds = new HashSet<>();
        for (WorkoutLineRequest line : request.lines()) {
            if (!exerciseIds.add(line.exerciseId())) {
                throw new BadRequestException("A workout cannot contain the same exercise twice");
            }
            Exercise exercise = exerciseService.require(line.exerciseId());
            validateLine(exercise, line);
        }
    }

    private void validateLine(Exercise exercise, WorkoutLineRequest line) {
        validateNonNegative(line.calories(), "Calories");
        switch (exercise.getTrackingMode()) {
            case REPS, SECONDS -> {
                if (line.calories() != null) {
                    throw new BadRequestException("Only cardio exercises allow top-level calories");
                }
            }
            case CARDIO -> {
            }
        }
        validateSegments(exercise, line.segments());
    }

    private void validateSegments(Exercise exercise, List<WorkoutSegmentRequest> segments) {
        for (WorkoutSegmentRequest segment : segments) {
            validateNonNegative(segment.weight(), "Weight");
            validateNonNegative(segment.speedKph(), "Speed");
            validateNonNegative(segment.inclinePercent(), "Incline");
            validateNonNegative(segment.resistanceLevel(), "Resistance");
            validateNonNegative(segment.calories(), "Calories");

            switch (exercise.getTrackingMode()) {
                case REPS -> validateRepSegment(segment);
                case SECONDS -> validateTimedSegment(segment);
                case CARDIO -> validateCardioSegment(segment);
            }
        }
    }

    private void validateRepSegment(WorkoutSegmentRequest segment) {
        if (segment.repetitions() == null || segment.repetitions() <= 0) {
            throw new BadRequestException("Rep-based exercises require repetitions");
        }
        if (segment.durationSeconds() != null || segment.speedKph() != null || segment.inclinePercent() != null || segment.resistanceLevel() != null || segment.calories() != null) {
            throw new BadRequestException("Rep-based exercises only allow repetitions and optional weight");
        }
    }

    private void validateTimedSegment(WorkoutSegmentRequest segment) {
        validateDuration(segment.durationSeconds(), "Timed exercises require a duration");
        if (segment.repetitions() != null || segment.speedKph() != null || segment.inclinePercent() != null || segment.resistanceLevel() != null || segment.calories() != null) {
            throw new BadRequestException("Timed exercises only allow duration and optional weight");
        }
    }

    private void validateCardioSegment(WorkoutSegmentRequest segment) {
        validateDuration(segment.durationSeconds(), "Cardio exercises require a duration");
        if (segment.repetitions() != null || segment.weight() != null || segment.calories() != null) {
            throw new BadRequestException("Cardio exercises do not allow repetitions, weight, or interval calories");
        }
    }

    private void validateDuration(Integer durationSeconds, String message) {
        if (durationSeconds == null || durationSeconds <= 0) {
            throw new BadRequestException(message);
        }
        if (durationSeconds % 5 != 0) {
            throw new BadRequestException("Duration must use 5-second steps");
        }
    }

    private void validateNonNegative(BigDecimal value, String name) {
        if (value != null && value.signum() < 0) {
            throw new BadRequestException(name + " cannot be negative");
        }
    }

    private void validateNonNegative(Integer value, String name) {
        if (value != null && value < 0) {
            throw new BadRequestException(name + " cannot be negative");
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
