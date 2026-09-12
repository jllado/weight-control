package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutSegmentRequest;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.StretchingUnit;
import java.math.BigDecimal;
import java.util.List;

final class WorkoutTargets {
    private WorkoutTargets() { }
    static void validate(Exercise exercise, StretchingUnit unit, List<WorkoutSegmentRequest> segments) {
        if (unit == StretchingUnit.BREATHS && exercise.getExerciseType() != ExerciseType.STRETCHING) {
            throw new BadRequestException("Only stretching exercises allow breaths");
        }
        for (WorkoutSegmentRequest segment : segments) {
            if (unit == StretchingUnit.BREATHS) {
                if (segment.breaths() == null || segment.breaths() <= 0) throw new BadRequestException("Enter a positive breath count for each hold");
                if (segment.durationSeconds() != null || segment.repetitions() != null || segment.weight() != null || segment.speedKph() != null || segment.distanceKm() != null || segment.inclinePercent() != null || segment.resistanceLevel() != null || segment.calories() != null) {
                    throw new BadRequestException("Breath-based holds only allow breaths");
                }
                continue;
            }
            if (segment.breaths() != null) throw new BadRequestException("Select Breaths to enter a breath count");
            if (exercise.getExerciseType() == ExerciseType.STRETCHING && segment.weight() != null) {
                throw new BadRequestException("Stretching exercises only allow duration");
            }
            validateNonNegative(segment.weight(), "Weight");
            validateNonNegative(segment.speedKph(), "Speed");
            validateNonNegative(segment.distanceKm(), "Distance");
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

    private static void validateRepSegment(WorkoutSegmentRequest segment) {
        if (segment.repetitions() == null || segment.repetitions() <= 0) {
            throw new BadRequestException("Rep-based exercises require repetitions");
        }
        if (segment.durationSeconds() != null || segment.speedKph() != null || segment.distanceKm() != null || segment.inclinePercent() != null || segment.resistanceLevel() != null || segment.calories() != null) {
            throw new BadRequestException("Rep-based exercises only allow repetitions and optional weight");
        }
    }

    private static void validateTimedSegment(WorkoutSegmentRequest segment) {
        validateDuration(segment.durationSeconds(), "Timed exercises require a duration");
        if (segment.repetitions() != null || segment.speedKph() != null || segment.distanceKm() != null || segment.inclinePercent() != null || segment.resistanceLevel() != null || segment.calories() != null) {
            throw new BadRequestException("Timed exercises only allow duration and optional weight");
        }
    }

    private static void validateCardioSegment(WorkoutSegmentRequest segment) {
        validateDuration(segment.durationSeconds(), "Cardio exercises require a duration");
        if (segment.repetitions() != null || segment.weight() != null || segment.calories() != null) {
            throw new BadRequestException("Cardio exercises do not allow repetitions, weight, or interval calories");
        }
    }

    private static void validateDuration(Integer durationSeconds, String message) {
        if (durationSeconds == null || durationSeconds <= 0) {
            throw new BadRequestException(message);
        }
        if (durationSeconds % 5 != 0) {
            throw new BadRequestException("Duration must use 5-second steps");
        }
    }

    private static void validateNonNegative(Number value, String name) {
        if (value != null && new BigDecimal(value.toString()).signum() < 0) throw new BadRequestException(name + " cannot be negative");
    }
}
