package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.SicknessSeverity;
import com.jllado.weightcontrol.domain.SicknessType;
import com.jllado.weightcontrol.domain.UserFitnessLevel;
import com.jllado.weightcontrol.domain.UserSex;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class HealthDataContextDtos {

    private HealthDataContextDtos() {
    }

    public record ReflectionContext(
        LocalDate selectedDate,
        LocalDate contextStart,
        LocalDate detailedStart,
        LocalDate baselineEnd,
        ProfileData profile,
        DataSemantics dataSemantics,
        CoachingPlanData activePlan,
        List<RecentReflectionData> recentReflections,
        List<DailyStatusData> dailyStatuses,
        List<HabitData> habits,
        List<RoutineData> routines,
        List<WeightData> weights,
        List<BloodPressureData> bloodPressures,
        List<MoodData> moods,
        List<SleepData> sleeps,
        List<CalorieData> calories,
        WorkoutContextData workouts,
        List<SicknessData> sicknesses,
        List<DecisionData> decisions,
        DecisionSummaryData decisionSummary,
        WeekProgress weekProgress,
        List<WeeklySummary> baselineWeeks
    ) {
    }

    public record DataSemantics(boolean recordedZeroCaloriesAreValid) {
    }

    public record CoachingPlanData(
        String goal,
        List<String> principles,
        List<String> priorities,
        List<String> actions,
        LocalDate startDate,
        LocalDate reviewDate,
        String notes,
        Instant updatedAt
    ) {
    }

    public record RecentReflectionData(
        LocalDate reflectionDate,
        String title,
        String summary,
        Integer planProgressScore,
        String planProgressRationale,
        List<String> positiveSignals,
        List<String> watchouts,
        List<String> nextActions
    ) {
    }

    public record ProfileData(
        Integer age,
        Integer heightCm,
        UserSex sex,
        UserFitnessLevel fitnessLevel,
        boolean takesMedication,
        int weeklyAverageCalorieMaximum,
        Map<String, Integer> typicalCaloriesByWeekday
    ) {
    }

    public record HabitData(String name, LocalDate startDate, Integer targetDays, LocalDate lastRecordedDate) {
    }

    public record RoutineData(
        String name,
        LocalDate startDate,
        List<String> types,
        int checkinCount,
        LocalDate lastCheckinDate
    ) {
    }

    public record DailyStatusData(
        LocalDate date,
        BigDecimal routinesPercentage,
        BigDecimal weightPercentage,
        BigDecimal bloodPressurePercentage,
        BigDecimal flexibilityPercentage,
        BigDecimal mindPercentage,
        BigDecimal routinesStatus,
        BigDecimal weightStatus,
        BigDecimal bloodPressureStatus,
        BigDecimal flexibilityStatus,
        BigDecimal mindStatus
    ) {
    }

    public record WeightData(
        LocalDate date,
        BigDecimal weightKg,
        BigDecimal fatPercentage,
        BigDecimal musclePercentage,
        BigDecimal weightChangeKg,
        BigDecimal fatChangeKg,
        BigDecimal muscleChangeKg
    ) {
    }

    public record BloodPressureData(
        LocalDate date,
        Integer systolic,
        Integer diastolic,
        Integer systolicChange,
        Integer diastolicChange
    ) {
    }

    public record MoodData(LocalDate date, MoodPeriod period, Integer value, String note) {
    }

    public record SleepData(
        LocalDate date,
        OffsetDateTime bedtimeStart,
        OffsetDateTime bedtimeEnd,
        Integer totalSleepSeconds,
        Integer deepSleepSeconds,
        Integer remSleepSeconds,
        Integer lightSleepSeconds,
        Integer awakeSeconds,
        BigDecimal averageHeartRate,
        Integer averageHrv
    ) {
    }

    public record CalorieData(LocalDate date, int calories) {
    }

    public record WorkoutContextData(List<WorkoutData> days, List<WorkoutExerciseData> exerciseSummaries) {
    }

    public record WorkoutData(
        LocalDate date,
        String note,
        List<String> exercises,
        Integer totalDurationSeconds,
        BigDecimal totalDistanceKm,
        Integer totalCalories,
        BigDecimal strengthVolumeKg
    ) {
    }

    public record WorkoutExerciseData(
        String exercise,
        ExerciseTrackingMode trackingMode,
        int sessionCount,
        int segmentCount,
        Integer totalRepetitions,
        Integer totalDurationSeconds,
        BigDecimal maximumWeightKg,
        BigDecimal strengthVolumeKg,
        BigDecimal totalDistanceKm,
        BigDecimal maximumSpeedKph,
        BigDecimal maximumInclinePercent,
        Integer maximumResistanceLevel,
        Integer totalCalories,
        BigDecimal averageHeartRate
    ) {
    }

    public record SicknessData(LocalDate date, SicknessType type, SicknessSeverity severity, String note) {
    }

    public record DecisionData(LocalDate date, DecisionOutcomeType outcome) {
    }

    public record DecisionMetricsData(long wins, long misses, BigDecimal winRate) {
    }

    public record DecisionSummaryData(
        DecisionMetricsData selectedDate,
        DecisionMetricsData rolling30Days,
        DecisionMetricsData previous30Days,
        DecisionMetricsData allTime,
        BigDecimal winRateChange,
        int currentWinStreak
    ) {
    }

    public record WeekProgress(
        boolean completeWeek,
        WeeklySummary currentPeriod,
        WeeklySummary previousComparablePeriod,
        WeeklySummary yearAgoComparablePeriod
    ) {
    }

    public record WeeklySummary(
        LocalDate startDate,
        LocalDate endDate,
        AverageStatus dashboard,
        int routineCheckins,
        AverageWeight weight,
        AverageBloodPressure bloodPressure,
        BigDecimal moodAverage,
        AverageSleep sleep,
        CalorieSummary calories,
        WorkoutSummary workouts,
        Map<String, Long> sicknessesByType,
        Map<String, Long> sicknessesBySeverity,
        DecisionMetricsData decisions
    ) {
    }

    public record AverageStatus(
        BigDecimal routinesPercentage,
        BigDecimal weightPercentage,
        BigDecimal bloodPressurePercentage,
        BigDecimal flexibilityPercentage,
        BigDecimal mindPercentage
    ) {
    }

    public record AverageWeight(BigDecimal weightKg, BigDecimal fatPercentage, BigDecimal musclePercentage) {
    }

    public record AverageBloodPressure(BigDecimal systolic, BigDecimal diastolic) {
    }

    public record AverageSleep(
        BigDecimal totalSleepSeconds,
        BigDecimal deepSleepSeconds,
        BigDecimal remSleepSeconds,
        BigDecimal awakeSeconds,
        BigDecimal averageHeartRate,
        BigDecimal averageHrv
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
}
