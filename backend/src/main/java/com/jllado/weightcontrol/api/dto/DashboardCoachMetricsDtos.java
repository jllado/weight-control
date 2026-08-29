package com.jllado.weightcontrol.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardCoachMetricsDtos {

    private DashboardCoachMetricsDtos() {
    }

    public record DashboardCoachMetricsResponse(
        CoachWeekResponse selectedWeek,
        CoachWeekResponse previousWeek,
        List<ReflectionMetricResponse> reflections,
        List<WorkoutMetricResponse> workouts,
        List<WeeklyWorkoutMetricResponse> weeklyWorkouts
    ) {
    }

    public record CoachWeekResponse(
        LocalDate startDate,
        LocalDate endDate,
        List<ReflectionMetricResponse> reflections,
        List<WorkoutMetricResponse> workouts,
        WorkoutTotalsResponse totals
    ) {
    }

    public record ReflectionMetricResponse(LocalDate date, String title, Integer planProgressScore, String planProgressRationale) {
    }

    public record WorkoutMetricResponse(
        LocalDate date,
        String dateFormat,
        String summary,
        Integer goalAlignmentScore,
        Integer estimatedTrainingDemandScore,
        boolean assessmentOutdated,
        WorkoutTotalsResponse totals
    ) {
    }

    public record WeeklyWorkoutMetricResponse(LocalDate startDate, LocalDate endDate, WorkoutTotalsResponse totals) {
    }

    public record WorkoutTotalsResponse(
        int workoutCount,
        int totalDurationSeconds,
        BigDecimal totalDistanceKm,
        int totalCalories,
        BigDecimal strengthVolumeKg
    ) {
    }
}
