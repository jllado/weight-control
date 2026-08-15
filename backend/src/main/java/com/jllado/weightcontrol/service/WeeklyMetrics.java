package com.jllado.weightcontrol.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class WeeklyMetrics {

    private WeeklyMetrics() {
    }

    public record Progress(
        boolean completeWeek,
        Summary currentPeriod,
        Summary previousComparablePeriod,
        Summary yearAgoComparablePeriod
    ) {
    }

    public record Summary(
        LocalDate startDate,
        LocalDate endDate,
        AverageStatus dashboard,
        int routineCheckins,
        RoutineCompletion routineCompletion,
        AverageWeight weight,
        AverageBloodPressure bloodPressure,
        BigDecimal moodAverage,
        int moodDayCount,
        AverageSleep sleep,
        CalorieSummary calories,
        WorkoutSummary workouts,
        Map<String, Long> sicknessesByType,
        Map<String, Long> sicknessesBySeverity,
        DecisionMetrics decisions
    ) {
    }

    public record RoutineCompletion(
        int completed,
        int opportunities,
        BigDecimal percentage,
        List<DailyRoutineCompletion> days
    ) {
    }

    public record DailyRoutineCompletion(LocalDate date, int completed, int opportunities, BigDecimal percentage) {
    }

    public record AverageStatus(
        BigDecimal routinesPercentage,
        BigDecimal weightPercentage,
        BigDecimal bloodPressurePercentage,
        BigDecimal flexibilityPercentage,
        BigDecimal mindPercentage
    ) {
    }

    public record AverageWeight(BigDecimal weightKg, BigDecimal fatPercentage, BigDecimal musclePercentage, int measurementCount) {
    }

    public record AverageBloodPressure(BigDecimal systolic, BigDecimal diastolic, int measurementCount) {
    }

    public record AverageSleep(
        BigDecimal totalSleepSeconds,
        BigDecimal deepSleepSeconds,
        BigDecimal remSleepSeconds,
        BigDecimal awakeSeconds,
        BigDecimal averageHeartRate,
        BigDecimal averageHrv,
        int nightCount
    ) {
    }

    public record CalorieSummary(
        int entryCount,
        int totalCalories,
        BigDecimal averageCalories,
        BigDecimal averageTargetCalories,
        BigDecimal averageDifferenceFromTarget
    ) {
    }

    public record WorkoutSummary(
        int workoutCount,
        int totalDurationSeconds,
        BigDecimal totalDistanceKm,
        int totalCalories,
        BigDecimal strengthVolumeKg
    ) {
    }

    public record DecisionMetrics(long wins, long misses, BigDecimal winRate) {
    }
}
