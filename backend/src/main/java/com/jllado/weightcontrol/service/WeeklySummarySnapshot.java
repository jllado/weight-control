package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.PersonalRecordUnit;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WeeklySummarySnapshot(
    int schemaVersion,
    LocalDate periodStart,
    LocalDate fridayDate,
    WeeklyMetrics.Progress progress,
    OutcomeMeasurements outcomes,
    BackPainSummary backPain,
    List<RoutineProgress> routines,
    GoalEvidence goalEvidence,
    List<PersonalRecordSnapshot> personalRecords,
    List<String> warnings,
    WeeklyNutritionSnapshot nutrition,
    List<WeeklyRoutineProgress> weeklyRoutines,
    EmailGoals emailGoals,
    WeeklyWorkoutTargets workoutTargets
) {

    public record EmailGoals(Instant capturedAt, BigDecimal weeklyAverageCalorieMaximum, BigDecimal inShapeBodyFatLimit, FixedRules rules) { }
    public record FixedRules(int version, int sleepMinimumSeconds, String sleepBasis, BigDecimal moodGoodMinimum,
        BigDecimal moodNeutralMinimum, List<BloodPressureRule> bloodPressureStages,
        List<ClassificationRule> bodyFatStages, List<ClassificationRule> sleepStages, int nutrientAssessmentVersion) {
        public FixedRules(int version, int sleepMinimumSeconds, String sleepBasis, BigDecimal moodGoodMinimum,
            BigDecimal moodNeutralMinimum, List<BloodPressureRule> bloodPressureStages) {
            this(version, sleepMinimumSeconds, sleepBasis, moodGoodMinimum, moodNeutralMinimum, bloodPressureStages, null, null, 0);
        }
    }
    public record ClassificationRule(String label, BigDecimal threshold, String color) { }

    public WeeklySummarySnapshot(int schemaVersion, LocalDate periodStart, LocalDate fridayDate,
        WeeklyMetrics.Progress progress, OutcomeMeasurements outcomes, BackPainSummary backPain,
        List<RoutineProgress> routines, GoalEvidence goalEvidence, List<PersonalRecordSnapshot> personalRecords,
        List<String> warnings, WeeklyNutritionSnapshot nutrition, List<WeeklyRoutineProgress> weeklyRoutines, EmailGoals emailGoals) {
        this(schemaVersion, periodStart, fridayDate, progress, outcomes, backPain, routines, goalEvidence, personalRecords,
            warnings, nutrition, weeklyRoutines, emailGoals, null);
    }
    public record BloodPressureRule(String label, Integer systolicAbove, Integer diastolicAbove, String color) { }

    public WeeklySummarySnapshot(int schemaVersion, LocalDate periodStart, LocalDate fridayDate,
        WeeklyMetrics.Progress progress, OutcomeMeasurements outcomes, BackPainSummary backPain,
        List<RoutineProgress> routines, GoalEvidence goalEvidence, List<PersonalRecordSnapshot> personalRecords,
        List<String> warnings, WeeklyNutritionSnapshot nutrition, List<WeeklyRoutineProgress> weeklyRoutines) {
        this(schemaVersion, periodStart, fridayDate, progress, outcomes, backPain, routines, goalEvidence, personalRecords,
            warnings, nutrition, weeklyRoutines, null);
    }

    public WeeklySummarySnapshot(int schemaVersion, LocalDate periodStart, LocalDate fridayDate,
        WeeklyMetrics.Progress progress, OutcomeMeasurements outcomes, BackPainSummary backPain,
        List<RoutineProgress> routines, GoalEvidence goalEvidence, List<PersonalRecordSnapshot> personalRecords,
        List<String> warnings, WeeklyNutritionSnapshot nutrition) {
        this(schemaVersion, periodStart, fridayDate, progress, outcomes, backPain, routines, goalEvidence, personalRecords, warnings, nutrition, null);
    }

    public record WeeklyRoutineProgress(String name, int completedWeeks, int eligibleWeeks, BigDecimal percentage, List<LocalDate> checkinDates) {}

    public WeeklySummarySnapshot(int schemaVersion, LocalDate periodStart, LocalDate fridayDate,
        WeeklyMetrics.Progress progress, OutcomeMeasurements outcomes, BackPainSummary backPain,
        List<RoutineProgress> routines, GoalEvidence goalEvidence, List<PersonalRecordSnapshot> personalRecords,
        List<String> warnings) {
        this(schemaVersion, periodStart, fridayDate, progress, outcomes, backPain, routines, goalEvidence, personalRecords, warnings, null);
    }

    public WeeklySummarySnapshot(
        int schemaVersion,
        LocalDate periodStart,
        LocalDate fridayDate,
        WeeklyMetrics.Progress progress,
        OutcomeMeasurements outcomes,
        List<RoutineProgress> routines,
        GoalEvidence goalEvidence,
        List<PersonalRecordSnapshot> personalRecords,
        List<String> warnings
    ) {
        this(schemaVersion, periodStart, fridayDate, progress, outcomes, null, routines, goalEvidence, personalRecords, warnings);
    }

    public record BackPainSummary(
        int checkInCount,
        int episodeCount,
        int painDayCount,
        Map<BackPainSeverity, Integer> episodesBySeverity,
        Map<BackRegion, Integer> episodesByRegion,
        Map<BackSide, Integer> episodesBySide,
        BackPainPeriodSummary lastWeek,
        BackPainPeriodSummary yearAgo
    ) {
        public BackPainSummary(
            int checkInCount,
            int episodeCount,
            int painDayCount,
            Map<BackPainSeverity, Integer> episodesBySeverity,
            Map<BackRegion, Integer> episodesByRegion,
            Map<BackSide, Integer> episodesBySide
        ) {
            this(checkInCount, episodeCount, painDayCount, episodesBySeverity, episodesByRegion, episodesBySide, null, null);
        }
    }

    public record BackPainPeriodSummary(int checkInCount, int episodeCount) {
    }

    public record OutcomeMeasurements(WeightMeasurement weight, BloodPressureMeasurement bloodPressure) {
    }

    public record WeightMeasurement(
        LocalDate measuredDate,
        BigDecimal weightKg,
        BigDecimal fatPercentage,
        BigDecimal fatKg,
        BigDecimal musclePercentage,
        BigDecimal muscleKg
    ) {
    }

    public record BloodPressureMeasurement(LocalDate measuredDate, int systolic, int diastolic) {
    }

    public record RoutineProgress(String name, int completedDays, int eligibleDays, BigDecimal percentage) {
    }

    public record GoalEvidence(
        boolean available,
        String goal,
        LocalDate startDate,
        LocalDate reviewDate,
        String unavailableReason
    ) {
    }

    public record PersonalRecordSnapshot(
        String label,
        String subjectLabel,
        BigDecimal value,
        PersonalRecordUnit unit,
        LocalDate date
    ) {
        public PersonalRecordSnapshot(String label, BigDecimal value, PersonalRecordUnit unit, LocalDate date) {
            this(label, null, value, unit, date);
        }
    }
}
