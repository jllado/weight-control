package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutLineRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutSegmentRequest;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseType;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    public Page<Workout> findDiaryPage(User user, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Diary page must be non-negative and size must be between 1 and 100");
        }
        Page<Workout> workouts = repository.findByUserOrderByWorkoutDateDesc(user, PageRequest.of(page, size));
        initializeLines(workouts.getContent());
        return workouts;
    }

    public List<Workout> findPreloadWorkouts(User user, LocalDate through) {
        List<Workout> workouts = repository.findPreloadSessions(user, through, PageRequest.of(0, 40));
        initializeLines(workouts);
        return workouts;
    }

    public DashboardWorkouts findDashboardWorkouts(User user, LocalDate date) {
        List<Workout> displayed = repository.findByUserAndWorkoutDateIn(user, List.of(date, date.minusWeeks(1)));
        List<Workout> preloads = repository.findPreloadSessions(user, date, PageRequest.of(0, 40));
        initializeLines(displayed);
        initializeLines(preloads);
        return new DashboardWorkouts(
            displayed.stream().filter(workout -> workout.getWorkoutDate().equals(date)).toList(),
            displayed.stream().filter(workout -> workout.getWorkoutDate().equals(date.minusWeeks(1))).toList(),
            preloads
        );
    }

    public Workout create(User user, WorkoutRequest request) {
        validateRequest(request);
        Workout workout = new Workout();
        workout.setUser(user);
        apply(workout, request);
        return repository.save(workout);
    }

    public Workout update(User user, Long id, WorkoutRequest request) {
        validateRequest(request);
        Workout workout = requireOwned(user, id);
        workout.setWorkoutDate(request.workoutDate());
        workout.setNote(blankToNull(request.note()));
        applyTiming(workout, request);
        workout.setAssessment(null);
        workout.getLines().clear();
        repository.flush();
        applyLines(workout, request);
        workout.setUpdatedAt(Instant.now());
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
        applyTiming(workout, request);
        applyLines(workout, request);
    }

    private void applyTiming(Workout workout, WorkoutRequest request) {
        workout.setStartTime(request.startTime());
        workout.setWarmUpMinutes(request.warmUpMinutes());
        workout.setTrainingMinutes(request.trainingMinutes());
        workout.setStretchingMinutes(request.stretchingMinutes());
        workout.setDurationMinutes(request.warmUpMinutes() == null ? request.durationMinutes()
            : Integer.valueOf(request.warmUpMinutes() + request.trainingMinutes() + request.stretchingMinutes()));
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
            line.setAverageHeartRate(lineRequest.averageHeartRate());
            for (int j = 0; j < lineRequest.segments().size(); j++) {
                WorkoutSegmentRequest segmentRequest = lineRequest.segments().get(j);
                WorkoutSegment segment = new WorkoutSegment();
                segment.setWorkoutLine(line);
                segment.setPosition(j);
                segment.setRepetitions(segmentRequest.repetitions());
                segment.setDurationSeconds(segmentRequest.durationSeconds());
                segment.setWeight(scale(segmentRequest.weight()));
                segment.setSpeedKph(scale(segmentRequest.speedKph()));
                segment.setDistanceKm(scale(segmentRequest.distanceKm()));
                segment.setInclinePercent(scale(segmentRequest.inclinePercent()));
                segment.setResistanceLevel(segmentRequest.resistanceLevel());
                line.getSegments().add(segment);
            }
            workout.getLines().add(line);
        }
    }

    private void validateRequest(WorkoutRequest request) {
        if (request.warmUpMinutes() != null || request.trainingMinutes() != null || request.stretchingMinutes() != null) {
            if (request.warmUpMinutes() == null || request.trainingMinutes() == null || request.stretchingMinutes() == null) {
                throw new BadRequestException("Enter all three duration values, using zero for phases you skipped");
            }
            long total = (long) request.warmUpMinutes() + request.trainingMinutes() + request.stretchingMinutes();
            if (total <= 0 || total > Integer.MAX_VALUE) {
                throw new BadRequestException("Total duration must be a positive number of minutes within the supported range");
            }
        }
        if (request.workoutDate().isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Workout date cannot be in the future");
        }
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
        validateNonNegative(line.averageHeartRate(), "Average heart rate");
        switch (exercise.getTrackingMode()) {
            case REPS, SECONDS -> {
                if (line.calories() != null || line.averageHeartRate() != null) {
                    throw new BadRequestException("Only cardio exercises allow top-level calories and average heart rate");
                }
            }
            case CARDIO -> {
            }
        }
        WorkoutTargets.validate(exercise, line.segments());
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

    public record DashboardWorkouts(List<Workout> currentWorkouts, List<Workout> previousWeekWorkouts, List<Workout> preloadWorkouts) {
    }
}
