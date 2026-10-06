package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.PersonalRecordUnit;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    List<String> warnings
) {

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
        Map<BackSide, Integer> episodesBySide
    ) {
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
        BigDecimal value,
        PersonalRecordUnit unit,
        LocalDate date
    ) {
    }
}
