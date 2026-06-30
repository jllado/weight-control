package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
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

public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    public record ExerciseRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String description,
        @NotNull ExerciseTrackingMode trackingMode
    ) {
    }

    public record ExerciseResponse(
        Long id,
        String name,
        String description,
        ExerciseTrackingMode trackingMode
    ) {
        public static ExerciseResponse from(Exercise exercise) {
            return new ExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getDescription(),
                exercise.getTrackingMode()
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
        @NotEmpty List<@Valid WorkoutSegmentRequest> segments
    ) {
    }

    public record WorkoutSegmentRequest(
        Integer repetitions,
        Integer durationSeconds,
        @DecimalMin("0.0") BigDecimal weight,
        @DecimalMin("0.0") BigDecimal speedKph,
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
        List<WorkoutLineResponse> lines
    ) {
        public static WorkoutResponse from(Workout workout) {
            return new WorkoutResponse(
                workout.getId(),
                DateTimes.formatDate(workout.getWorkoutDate()),
                workout.getWorkoutDate(),
                workout.getNote(),
                workout.getLines().stream().map(WorkoutLineResponse::from).toList()
            );
        }
    }

    public record WorkoutLineResponse(
        Long exerciseId,
        String exerciseName,
        String exerciseDescription,
        ExerciseTrackingMode trackingMode,
        Integer position,
        Integer calories,
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
                line.getPosition(),
                line.getCalories(),
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
        BigDecimal inclinePercent,
        Integer resistanceLevel
    ) {
        public static CardioIntervalResponse from(WorkoutSegment segment) {
            return new CardioIntervalResponse(
                segment.getPosition(),
                segment.getDurationSeconds(),
                segment.getSpeedKph(),
                segment.getInclinePercent(),
                segment.getResistanceLevel()
            );
        }
    }
}
