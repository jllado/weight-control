package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.HistoryEventResponse;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.HistoryEventResponse;

public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    public record ExerciseRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String description,
        @NotNull ExerciseTrackingMode trackingMode,
        @NotNull ExerciseType exerciseType,
        boolean defaultWarmUp,
        @DecimalMin("1") Integer defaultRepetitions
    ) {
        public ExerciseRequest(String name, String description, ExerciseTrackingMode trackingMode) {
            this(name, description, trackingMode, ExerciseType.TRAINING, false, null);
        }
    }

    public record ExerciseResponse(
        Long id,
        String name,
        String description,
        ExerciseTrackingMode trackingMode,
        ExerciseType exerciseType,
        boolean defaultWarmUp,
        Integer defaultRepetitions
    ) {
        public static ExerciseResponse from(Exercise exercise) {
            return new ExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getDescription(),
                exercise.getTrackingMode(),
                exercise.getExerciseType(),
                exercise.isDefaultWarmUp(),
                exercise.getDefaultRepetitions()
            );
        }
    }

    public record WorkoutRequest(
        @NotNull LocalDate workoutDate,
        @Size(max = 500) String note,
        @NotEmpty List<@Valid WorkoutLineRequest> lines
    ) {
    }

    public record WorkoutLineRequest(
        @NotNull Long exerciseId,
        @DecimalMin("0") Integer calories,
        @DecimalMin("0") Integer averageHeartRate,
        @NotEmpty List<@Valid WorkoutSegmentRequest> segments
    ) {
    }

    public record WorkoutSegmentRequest(
        Integer repetitions,
        Integer durationSeconds,
        @DecimalMin("0.0") BigDecimal weight,
        @DecimalMin("0.0") BigDecimal speedKph,
        @DecimalMin("0.0") BigDecimal distanceKm,
        @DecimalMin("0.0") BigDecimal inclinePercent,
        @DecimalMin("0") Integer resistanceLevel,
        @DecimalMin("0") Integer calories
    ) {
    }

    public record WorkoutResponse(
        Long id,
        String workoutDateFormat,
        LocalDate workoutDate,
        String note,
        List<WorkoutLineResponse> lines,
        WorkoutAssessmentResponse assessment
    ) {
        public static WorkoutResponse from(Workout workout) {
            return new WorkoutResponse(
                workout.getId(),
                DateTimes.formatDate(workout.getWorkoutDate()),
                workout.getWorkoutDate(),
                workout.getNote(),
                workout.getLines().stream().map(WorkoutLineResponse::from).toList(),
                workout.getAssessment() == null ? null : WorkoutAssessmentResponse.from(workout.getAssessment())
            );
        }
    }

    public record DashboardWorkoutResponse(
        WorkoutResponse currentWorkout,
        WorkoutResponse previousWeekWorkout,
        List<WorkoutResponse> preloadWorkouts,
        List<HistoryEventResponse> recordEvents
    ) {
    }

    public record WorkoutDiaryPageResponse(
        List<WorkoutResponse> items,
        List<HistoryEventResponse> recordEvents,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record WorkoutLineResponse(
        Long exerciseId,
        String exerciseName,
        String exerciseDescription,
        ExerciseTrackingMode trackingMode,
        ExerciseType exerciseType,
        Integer position,
        Integer calories,
        Integer averageHeartRate,
        List<WorkoutSetResponse> sets,
        List<CardioIntervalResponse> intervals
    ) {
        public static WorkoutLineResponse from(WorkoutLine line) {
            ExerciseTrackingMode mode = line.getExercise().getTrackingMode();
            List<WorkoutSetResponse> sets = mode == ExerciseTrackingMode.CARDIO
                ? List.of()
                : line.getSegments().stream().map(WorkoutSetResponse::from).toList();
            List<CardioIntervalResponse> intervals = mode == ExerciseTrackingMode.CARDIO
                ? line.getSegments().stream().map(CardioIntervalResponse::from).toList()
                : List.of();
            return new WorkoutLineResponse(
                line.getExercise().getId(),
                line.getExercise().getName(),
                line.getExercise().getDescription(),
                mode,
                line.getExercise().getExerciseType(),
                line.getPosition(),
                line.getCalories(),
                line.getAverageHeartRate(),
                sets,
                intervals
            );
        }
    }

    public record WorkoutSetResponse(
        Integer position,
        Integer repetitions,
        Integer durationSeconds,
        BigDecimal weight
    ) {
        public static WorkoutSetResponse from(WorkoutSegment segment) {
            return new WorkoutSetResponse(
                segment.getPosition(),
                segment.getRepetitions(),
                segment.getDurationSeconds(),
                segment.getWeight()
            );
        }
    }

    public record CardioIntervalResponse(
        Integer position,
        Integer durationSeconds,
        BigDecimal speedKph,
        BigDecimal distanceKm,
        BigDecimal inclinePercent,
        Integer resistanceLevel
    ) {
        public static CardioIntervalResponse from(WorkoutSegment segment) {
            return new CardioIntervalResponse(
                segment.getPosition(),
                segment.getDurationSeconds(),
                segment.getSpeedKph(),
                segment.getDistanceKm(),
                segment.getInclinePercent(),
                segment.getResistanceLevel()
            );
        }
    }
}
