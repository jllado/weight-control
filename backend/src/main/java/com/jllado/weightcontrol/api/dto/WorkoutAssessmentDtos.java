package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.api.dto.CoachDtos.HealthConstraintData;
import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachingPlanResponse;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutAssessment;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class WorkoutAssessmentDtos {

    private WorkoutAssessmentDtos() {
    }

    public record SaveWorkoutAssessmentRequest(
        @Min(1) @Max(10) int goalAlignmentScore,
        @Min(1) @Max(10) int estimatedTrainingDemandScore,
        @NotBlank String rationale,
        @NotBlank String strength,
        @NotBlank String improvement,
        @NotBlank String nextWorkoutAction,
        @NotNull Instant planUpdatedAt,
        @NotBlank String workoutContextToken,
        @AssertTrue boolean confirmed
    ) {
    }

    public record WorkoutAssessmentResponse(
        int goalAlignmentScore,
        int estimatedTrainingDemandScore,
        String rationale,
        String strength,
        String improvement,
        String nextWorkoutAction,
        String goalSnapshot,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static WorkoutAssessmentResponse from(WorkoutAssessment assessment) {
            return new WorkoutAssessmentResponse(
                assessment.getGoalAlignmentScore(),
                assessment.getEstimatedTrainingDemandScore(),
                assessment.getRationale(),
                assessment.getStrength(),
                assessment.getImprovement(),
                assessment.getNextWorkoutAction(),
                assessment.getGoalSnapshot(),
                assessment.getCreatedAt(),
                assessment.getUpdatedAt()
            );
        }
    }

    public record WorkoutAssessmentContextResponse(
        AssessmentDayData workout,
        CoachingPlanResponse activePlan,
        List<HealthConstraintData> activeConstraints,
        List<AssessmentDayData> recentComparableTraining,
        WorkoutAssessmentResponse currentAssessment,
        Instant planUpdatedAt,
        String workoutContextToken
    ) {
    }

    public record AssessmentDayData(LocalDate date, List<AssessmentWorkoutData> sessions) {}

    public record AssessmentWorkoutData(
        String sessionReference,
        LocalDate date,
        String note,
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm") java.time.LocalTime startTime,
        Integer durationMinutes,
        Integer warmUpMinutes,
        Integer trainingMinutes,
        Integer stretchingMinutes,
        Integer cardioMinutes,
        List<AssessmentWorkoutLineData> lines
    ) {
        public static AssessmentWorkoutData from(Workout workout) {
            return new AssessmentWorkoutData(
                workout.getSessionReference(),
                workout.getWorkoutDate(),
                workout.getNote(),
                workout.getStartTime(), workout.getDurationMinutes(), workout.getWarmUpMinutes(), workout.getTrainingMinutes(), workout.getStretchingMinutes(), workout.getCardioMinutes(),
                workout.getLines().stream().map(AssessmentWorkoutLineData::from).toList()
            );
        }

        public static AssessmentWorkoutData comparable(Workout workout, Set<Long> exerciseIds) {
            return new AssessmentWorkoutData(
                workout.getSessionReference(),
                workout.getWorkoutDate(),
                workout.getNote(),
                workout.getStartTime(), workout.getDurationMinutes(), workout.getWarmUpMinutes(), workout.getTrainingMinutes(), workout.getStretchingMinutes(), workout.getCardioMinutes(),
                workout.getLines().stream()
                    .filter(line -> exerciseIds.contains(line.getExercise().getId()))
                    .map(AssessmentWorkoutLineData::from)
                    .toList()
            );
        }
    }

    public record AssessmentWorkoutLineData(
        String exercise,
        String description,
        ExerciseTrackingMode trackingMode,
        ExerciseType exerciseType,
        Integer calories,
        Integer averageHeartRate,
        List<AssessmentWorkoutSegmentData> segments,
        com.jllado.weightcontrol.domain.StretchingUnit stretchingUnit
    ) {
        public static AssessmentWorkoutLineData from(WorkoutLine line) {
            return new AssessmentWorkoutLineData(
                line.getExercise().getName(),
                line.getExercise().getDescription(),
                line.getExercise().getTrackingMode(),
                line.getExercise().getExerciseType(),
                line.getCalories(),
                line.getAverageHeartRate(),
                line.getSegments().stream().map(AssessmentWorkoutSegmentData::from).toList(),
                line.getStretchingUnit()
            );
        }
    }

    public record AssessmentWorkoutSegmentData(
        Integer repetitions,
        Integer durationSeconds,
        BigDecimal weightKg,
        BigDecimal speedKph,
        BigDecimal distanceKm,
        BigDecimal inclinePercent,
        Integer resistanceLevel,
        Integer breaths
    ) {
        public static AssessmentWorkoutSegmentData from(WorkoutSegment segment) {
            return new AssessmentWorkoutSegmentData(
                segment.getRepetitions(),
                segment.getDurationSeconds(),
                segment.getWeight(),
                segment.getSpeedKph(),
                segment.getDistanceKm(),
                segment.getInclinePercent(),
                segment.getResistanceLevel(),
                segment.getBreaths()
            );
        }
    }
}
