package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.HistoryEventResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.PersonalRecordSourceResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.PersonalRecordSubjectResponse;
import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.PersonalRecordDirection;
import com.jllado.weightcontrol.domain.PersonalRecordDomain;
import com.jllado.weightcontrol.domain.PersonalRecordEventKind;
import com.jllado.weightcontrol.domain.PersonalRecordMetric;
import com.jllado.weightcontrol.domain.PersonalRecordSourceType;
import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.SicknessSeverity;
import com.jllado.weightcontrol.domain.SicknessType;
import com.jllado.weightcontrol.domain.PersonalRecordUnit;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.ComparisonStatus;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeeklySummaryEmailViewFactoryTest {

    private final WeeklyMetricsCalculator calculator = new WeeklyMetricsCalculator();
    private final WeeklySummaryEmailViewFactory factory = new WeeklySummaryEmailViewFactory();

    @Test
    void viewShowsBothComparisonsAndMissingMeasurementData() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        List<DailyStatus> statuses = new ArrayList<>();
        statuses.addAll(statuses(end.minusDays(6), 3, 4));
        statuses.addAll(statuses(end.minusWeeks(1).minusDays(6), 2, 4));
        statuses.addAll(statuses(end.minusWeeks(52).minusDays(6), 1, 4));
        WeeklyMetricsCalculator.Input input = new WeeklyMetricsCalculator.Input(
            statuses, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );

        WeeklySummaryEmailView view = factory.create(user, calculator.progress(user, end, input), emptyMeasurements(), "https://weight.example");

        assertEquals("75%", view.headlineValue());
        assertEquals("+25.0 pp vs last week", view.previousRoutineComparison().text());
        assertEquals("↑ +25.0 pp vs last week", view.previousRoutineComparison().displayText());
        assertEquals(ComparisonStatus.IMPROVED, view.previousRoutineComparison().status());
        assertEquals("+50.0 pp vs 52 weeks ago", view.yearAgoRoutineComparison().text());
        assertEquals(7, view.days().size());
        assertEquals("Not recorded", view.cardRows().get(0).left().value());
        assertEquals(ComparisonStatus.UNKNOWN, view.cardRows().get(0).left().previousComparison().status());
        assertTrue(view.subject().contains("8 Aug–14 Aug"));
    }

    @Test
    void comparisonsClassifyHigherAndLowerMetricsAgainstBothBaselines() {
        LocalDate currentStart = LocalDate.of(2026, 8, 8);
        WeeklyMetrics.Summary current = summary(currentStart, "80", "1900", "21600", "4.0", "67", "120", "80", 4, "80");
        WeeklyMetrics.Summary previous = summary(currentStart.minusWeeks(1), "70", "2000", "21000", "3.0", "68", "125", "85", 3, "70");
        WeeklyMetrics.Summary yearAgo = summary(currentStart.minusWeeks(52), "90", "1800", "22000", "4.5", "66", "115", "75", 5, "90");

        WeeklySummaryMeasurements measurements = measurements(
            currentStart.plusDays(6), "67", 120, 80,
            currentStart.minusDays(1), "68", 125, 85,
            currentStart.minusWeeks(52).minusDays(1), "66", 115, 75
        );
        WeeklySummaryEmailView view = factory.create(user(), new WeeklyMetrics.Progress(true, current, previous, yearAgo), measurements, "https://weight.example");

        assertEquals(ComparisonStatus.IMPROVED, view.previousRoutineComparison().status());
        assertEquals(ComparisonStatus.WORSENED, view.yearAgoRoutineComparison().status());
        assertTrue(comparisons(view, true).stream().allMatch(comparison -> comparison.status() == ComparisonStatus.IMPROVED));
        assertTrue(comparisons(view, false).stream().allMatch(comparison -> comparison.status() == ComparisonStatus.WORSENED));
    }

    @Test
    void comparisonsTreatDisplayedZeroAndMixedBloodPressureAsUnchanged() {
        LocalDate currentStart = LocalDate.of(2026, 8, 8);
        WeeklyMetrics.Summary current = summary(currentStart, "75", "2000", "21600", "4.0", "68.04", "120", "85", 3, "80");
        WeeklyMetrics.Summary baseline = summary(currentStart.minusWeeks(1), "75", "2000", "21600", "4.0", "68.00", "125", "80", 3, "80");

        WeeklySummaryMeasurements measurements = measurements(
            currentStart.plusDays(6), "68.04", 120, 85,
            currentStart.minusDays(1), "68.00", 125, 80,
            currentStart.minusWeeks(52).minusDays(1), "68.00", 125, 80
        );
        WeeklySummaryEmailView view = factory.create(user(), new WeeklyMetrics.Progress(true, current, baseline, baseline), measurements, "https://weight.example");

        assertEquals(ComparisonStatus.UNCHANGED, view.cardRows().get(1).right().previousComparison().status());
        assertTrue(view.cardRows().get(1).right().previousComparison().displayText().startsWith("→ 0.0 kg"));
        assertEquals(ComparisonStatus.UNCHANGED, view.cardRows().get(2).left().previousComparison().status());
    }

    @Test
    void sleepValuesUseMinutesBelowAnHourAndDecimalHoursOtherwise() {
        LocalDate currentStart = LocalDate.of(2026, 8, 8);
        WeeklyMetrics.Summary current = summary(currentStart, "75", "2000", "21600", "4.0", "68", "120", "80", 3, "80");
        WeeklyMetrics.Summary previous = summary(currentStart.minusWeeks(1), "75", "2000", "19800", "4.0", "68", "120", "80", 3, "80");
        WeeklyMetrics.Summary yearAgo = summary(currentStart.minusWeeks(52), "75", "2000", "1800", "4.0", "68", "120", "80", 3, "80");

        WeeklySummaryEmailView view = factory.create(user(), new WeeklyMetrics.Progress(true, current, previous, yearAgo), emptyMeasurements(), "https://weight.example");

        WeeklySummaryEmailView.MetricCard sleep = view.cardRows().get(0).right();
        assertEquals("6.0 h", sleep.value());
        assertEquals("+30 min vs last week · 7/7 nights", sleep.previousComparison().text());
        assertEquals("+5.5 h vs 52 weeks ago · 7/7 nights", sleep.yearAgoComparison().text());

        WeeklySummaryEmailView shortSleepView = factory.create(user(), new WeeklyMetrics.Progress(true, yearAgo, current, previous), emptyMeasurements(), "https://weight.example");
        WeeklySummaryEmailView.MetricCard shortSleep = shortSleepView.cardRows().get(0).right();
        assertEquals("30 min", shortSleep.value());
        assertEquals("−5.5 h vs last week · 7/7 nights", shortSleep.previousComparison().text());
    }

    @Test
    void latestMeasurementCardsShowMeasurementDatesAndSnapshotComparisons() {
        LocalDate currentStart = LocalDate.of(2026, 8, 8);
        WeeklyMetrics.Summary summary = summary(currentStart, "75", "2000", "21600", "4.0", "1", "1", "1", 3, "80");
        WeeklySummaryMeasurements measurements = measurements(
            LocalDate.of(2026, 8, 3), "68.40", 121, 81,
            LocalDate.of(2026, 7, 29), "68.90", 126, 84,
            LocalDate.of(2025, 7, 1), "72.00", 130, 86
        );

        WeeklySummaryEmailView view = factory.create(
            user(),
            new WeeklyMetrics.Progress(true, summary, summary, summary),
            measurements,
            "https://weight.example"
        );

        WeeklySummaryEmailView.MetricCard weight = view.cardRows().get(1).right();
        WeeklySummaryEmailView.MetricCard bloodPressure = view.cardRows().get(2).left();
        assertEquals("Latest weight", weight.label());
        assertEquals("68.4 kg", weight.value());
        assertEquals("Measured 3 Aug", weight.detail());
        assertEquals("-0.5 kg vs last week · measured 29 Jul", weight.previousComparison().text());
        assertEquals("Latest blood pressure", bloodPressure.label());
        assertEquals("121 / 81 mmHg", bloodPressure.value());
        assertEquals("Measured 3 Aug", bloodPressure.detail());
        assertEquals("-5 / -3 mmHg vs last week · measured 29 Jul", bloodPressure.previousComparison().text());
    }

    @Test
    void dailyIndicatorsUseTheExistingRoutinePercentageColorBands() {
        LocalDate start = LocalDate.of(2026, 8, 8);
        List<DailyStatus> statuses = List.of(
            status(start, 4, 4),
            status(start.plusDays(1), 3, 4),
            status(start.plusDays(2), 2, 4),
            status(start.plusDays(3), 9, 20),
            status(start.plusDays(4), 7, 20),
            status(start.plusDays(5), 0, 0),
            status(start.plusDays(6), 4, 4)
        );
        WeeklyMetrics.Progress progress = calculator.progress(user(), start.plusDays(6), new WeeklyMetricsCalculator.Input(
            statuses, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        ));

        WeeklySummaryEmailView view = factory.create(user(), progress, emptyMeasurements(), "https://weight.example");

        assertEquals(List.of("day-value--perfect", "day-value--good", "day-value--normal", "day-value--fail", "day-value--bad", "day-value--unknown", "day-value--perfect"),
            view.days().stream().map(WeeklySummaryEmailView.DayView::cssClass).toList());
    }

    @Test
    void viewIncludesFormattedImprovedRecordRows() {
        LocalDate end = LocalDate.of(2026, 8, 14);
        WeeklyMetrics.Progress progress = calculator.progress(user(), end, new WeeklyMetricsCalculator.Input(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        ));
        HistoryEventResponse record = new HistoryEventResponse(
            "event", PersonalRecordMetric.BODY_WEIGHT_MAXIMUM, "Highest weight", PersonalRecordDomain.BODY, PersonalRecordDirection.MAXIMUM,
            PersonalRecordEventKind.IMPROVED, new BigDecimal("70.5"), new BigDecimal("70"), PersonalRecordUnit.KG, end, true,
            new PersonalRecordSubjectResponse("BODY", null, "Weight"), null,
            new PersonalRecordSourceResponse(PersonalRecordSourceType.WEIGHT, 1L, null, null)
        );

        WeeklySummaryEmailView view = factory.create(user(), progress, emptyMeasurements(), List.of(record), "https://weight.example");

        assertEquals("Highest weight", view.records().getFirst().label());
        assertEquals("70.5 kg", view.records().getFirst().value());
        assertEquals("14 Aug", view.records().getFirst().date());
    }

    @Test
    void savedSnapshotEmailIncludesDatedBodyCompositionAndOnlyBelowThresholdRoutineWatchouts() {
        User user = user();
        LocalDate friday = LocalDate.of(2026, 8, 14);
        Weight fridayWeight = new Weight();
        fridayWeight.setMeasuredAt(DateTimes.startOfDay(friday).plusHours(8));
        fridayWeight.setWeight(new BigDecimal("69"));
        fridayWeight.setFatPercentage(new BigDecimal("20"));
        fridayWeight.setFat(new BigDecimal("13.8"));
        fridayWeight.setMusclePercentage(new BigDecimal("75"));
        fridayWeight.setMuscle(new BigDecimal("51.8"));
        BloodPressure fridayPressure = new BloodPressure();
        fridayPressure.setMeasuredAt(DateTimes.startOfDay(friday).plusHours(8));
        fridayPressure.setUpper(120);
        fridayPressure.setLower(80);
        WeeklyMetrics.Progress progress = calculator.progress(user, friday, new WeeklyMetricsCalculator.Input(
            List.of(), List.of(fridayWeight), List.of(fridayPressure), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        ));
        WeeklySummarySnapshot snapshot = new WeeklySummarySnapshot(
            1, LocalDate.of(2026, 8, 8), friday, progress,
            new WeeklySummarySnapshot.OutcomeMeasurements(
                new WeeklySummarySnapshot.WeightMeasurement(friday.plusDays(1), new BigDecimal("69"), new BigDecimal("20"), new BigDecimal("13.8"), new BigDecimal("75"), new BigDecimal("51.8")),
                new WeeklySummarySnapshot.BloodPressureMeasurement(friday.plusDays(2), 121, 81)
            ),
            new WeeklySummarySnapshot.BackPainSummary(3, 2, 2,
                Map.of(BackPainSeverity.MILD, 1, BackPainSeverity.MODERATE, 1),
                Map.of(BackRegion.LOWER, 2), Map.of(BackSide.LEFT, 1, BackSide.CENTER, 1)),
            List.of(
                new WeeklySummarySnapshot.RoutineProgress("Watch", 2, 7, new BigDecimal("28.57")),
                new WeeklySummarySnapshot.RoutineProgress("Exactly 60%", 3, 5, new BigDecimal("60.00"))
            ),
            new WeeklySummarySnapshot.GoalEvidence(false, null, null, null, "Historical plan unavailable."),
            List.of(), List.of()
        );

        WeeklySummaryEmailView view = factory.create(user, snapshot, null, "https://weight.example/");

        assertEquals("https://weight.example/weekly-summaries/2026-08-14", view.appUrl());
        assertTrue(view.outcomes().weight().contains("fat 20.0% · 13.8 kg"));
        assertTrue(view.outcomes().weight().contains("15 August 2026"));
        assertTrue(view.outcomes().bloodPressure().contains("16 August 2026"));
        assertEquals(1, view.routineWatchouts().size());
        assertEquals("Watch", view.routineWatchouts().getFirst().name());
        assertTrue(view.cardRows().get(1).right().detail().contains("fat"));
        WeeklySummaryEmailView.MetricCard backPain = view.cardRows().stream()
            .flatMap(row -> java.util.stream.Stream.of(row.left(), row.right()))
            .filter(card -> card.label().equals("Back pain"))
            .findFirst().orElseThrow();
        assertEquals("2 episodes · 2 days", backPain.value());
        assertTrue(backPain.detail().contains("Severity: Mild: 1, Moderate: 1"));
        assertTrue(backPain.detail().contains("Regions: Lower: 2"));
        assertTrue(backPain.detail().contains("Sides: Left: 1, Center: 1"));
    }

    @Test
    void sicknessTotalsAndSeverityAppearWithNeutralPeriodComparisons() {
        User user = user();
        LocalDate friday = LocalDate.of(2026, 8, 14);
        Sickness cold = new Sickness();
        cold.setUser(user);
        cold.setSicknessDate(friday.minusDays(1));
        cold.setType(SicknessType.COLD);
        cold.setSeverity(SicknessSeverity.LOW);
        WeeklyMetrics.Progress progress = calculator.progress(user, friday, new WeeklyMetricsCalculator.Input(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(cold), List.of(), List.of()
        ));

        WeeklySummaryEmailView view = factory.create(user, progress, emptyMeasurements(), "https://weight.example");

        WeeklySummaryEmailView.MetricCard sickness = view.cardRows().get(3).left();
        assertEquals("Sickness records", sickness.label());
        assertEquals("1 record", sickness.value());
        assertEquals("Types: COLD: 1 · Severity: LOW: 1", sickness.detail());
        assertEquals("1 recorded vs 0 last week", sickness.previousComparison().text());
        assertEquals(ComparisonStatus.UNKNOWN, sickness.previousComparison().status());
    }

    @Test
    void workoutCardShowsRecordedTotalsAndCoverage() {
        LocalDate start = LocalDate.of(2026, 8, 8);
        WeeklyMetrics.Summary current = summary(start, "75", "2000", "21600", "4.0", "68", "120", "80", 2, "80");
        current = withWorkouts(current, new WeeklyMetrics.WorkoutSummary(
            2, 5400, new BigDecimal("12.34"), 456, new BigDecimal("250.0"), 2, 3, 2, 24
        ));
        WeeklyMetrics.Summary baseline = summary(start.minusWeeks(1), "70", "2000", "21600", "4.0", "68", "120", "80", 1, "80");

        WeeklySummaryEmailView view = factory.create(
            user(), new WeeklyMetrics.Progress(true, current, baseline, baseline), emptyMeasurements(), "https://weight.example"
        );

        WeeklySummaryEmailView.MetricCard workout = view.cardRows().get(2).right();
        assertEquals("2 sessions", workout.value());
        assertEquals(
            "Timed activity: 1 h 30 min (2 records) · Distance: 12.3 km (3 records) · Workout energy: 456 kcal (2 records) · Strength volume: 250.0 kg (24 sets)",
            workout.detail()
        );
    }

    @Test
    void dailyStatusCardShowsEveryTrackedCompletionArea() {
        User user = user();
        LocalDate friday = LocalDate.of(2026, 8, 14);
        DailyStatus status = status(friday, 2, 4);
        status.setTotalWeightRoutines(2);
        status.setWeightPercentage(new BigDecimal("50"));
        status.setTotalBloodPressureRoutines(2);
        status.setBloodPressurePercentage(BigDecimal.ZERO);
        status.setTotalFlexibilityRoutines(4);
        status.setFlexibilityPercentage(new BigDecimal("75"));
        status.setTotalMindRoutines(4);
        status.setMindPercentage(new BigDecimal("50"));
        WeeklyMetrics.Progress progress = calculator.progress(user, friday, new WeeklyMetricsCalculator.Input(
            List.of(status), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        ));

        WeeklySummaryEmailView view = factory.create(user, progress, emptyMeasurements(), "https://weight.example");

        assertEquals(
            "Routines 50% · Weight 50% · Blood pressure 0% · Flexibility 75% · Mind 50%",
            view.cardRows().get(4).left().detail()
        );
    }

    private List<WeeklySummaryEmailView.Comparison> comparisons(WeeklySummaryEmailView view, boolean previous) {
        return view.cardRows().stream()
            .flatMap(row -> row.right() == null ? java.util.stream.Stream.of(row.left()) : java.util.stream.Stream.of(row.left(), row.right()))
            .filter(card -> !card.label().equals("Sickness records") && !card.label().equals("Daily status completion"))
            .map(card -> previous ? card.previousComparison() : card.yearAgoComparison())
            .toList();
    }

    private WeeklySummaryMeasurements emptyMeasurements() {
        WeeklySummaryMeasurements.PeriodMeasurements empty = new WeeklySummaryMeasurements.PeriodMeasurements(null, null);
        return new WeeklySummaryMeasurements(empty, empty, empty);
    }

    private WeeklySummaryMeasurements measurements(
        LocalDate currentDate,
        String currentWeight,
        int currentUpper,
        int currentLower,
        LocalDate previousDate,
        String previousWeight,
        int previousUpper,
        int previousLower,
        LocalDate yearAgoDate,
        String yearAgoWeight,
        int yearAgoUpper,
        int yearAgoLower
    ) {
        return new WeeklySummaryMeasurements(
            measurements(currentDate, currentWeight, currentUpper, currentLower),
            measurements(previousDate, previousWeight, previousUpper, previousLower),
            measurements(yearAgoDate, yearAgoWeight, yearAgoUpper, yearAgoLower)
        );
    }

    private WeeklySummaryMeasurements.PeriodMeasurements measurements(LocalDate date, String weightValue, int upper, int lower) {
        Weight weight = new Weight();
        weight.setMeasuredAt(DateTimes.startOfDay(date).plusHours(8));
        weight.setWeight(new BigDecimal(weightValue));
        BloodPressure bloodPressure = new BloodPressure();
        bloodPressure.setMeasuredAt(DateTimes.startOfDay(date).plusHours(9));
        bloodPressure.setUpper(upper);
        bloodPressure.setLower(lower);
        return new WeeklySummaryMeasurements.PeriodMeasurements(weight, bloodPressure);
    }

    private WeeklyMetrics.Summary summary(
        LocalDate start,
        String routinePercentage,
        String calories,
        String sleepSeconds,
        String mood,
        String weight,
        String systolic,
        String diastolic,
        int workouts,
        String decisionWinRate
    ) {
        BigDecimal routine = new BigDecimal(routinePercentage);
        BigDecimal calorieAverage = new BigDecimal(calories);
        return new WeeklyMetrics.Summary(
            start,
            start.plusDays(6),
            null,
            routine.intValue(),
            new WeeklyMetrics.RoutineCompletion(routine.intValue(), 100, routine, List.of()),
            new WeeklyMetrics.AverageWeight(new BigDecimal(weight), null, null, null, null, 7),
            new WeeklyMetrics.AverageBloodPressure(new BigDecimal(systolic), new BigDecimal(diastolic), 7),
            new BigDecimal(mood),
            7,
            new WeeklyMetrics.AverageSleep(new BigDecimal(sleepSeconds), null, null, null, null, null, 7),
            new WeeklyMetrics.CalorieSummary(7, calorieAverage.multiply(BigDecimal.valueOf(7)).intValue(), calorieAverage, BigDecimal.valueOf(2000), calorieAverage.subtract(BigDecimal.valueOf(2000))),
            new WeeklyMetrics.WorkoutSummary(workouts, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, 0, 0, 0),
            Map.of(),
            Map.of(),
            new WeeklyMetrics.DecisionMetrics(8, 2, new BigDecimal(decisionWinRate))
        );
    }

    private WeeklyMetrics.Summary withWorkouts(WeeklyMetrics.Summary summary, WeeklyMetrics.WorkoutSummary workouts) {
        return new WeeklyMetrics.Summary(
            summary.startDate(), summary.endDate(), summary.dashboard(), summary.routineCheckins(), summary.routineCompletion(),
            summary.weight(), summary.bloodPressure(), summary.moodAverage(), summary.moodDayCount(), summary.sleep(), summary.calories(),
            workouts, summary.sicknessesByType(), summary.sicknessesBySeverity(), summary.decisions()
        );
    }

    private List<DailyStatus> statuses(LocalDate start, int completed, int opportunities) {
        List<DailyStatus> statuses = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            statuses.add(status(start.plusDays(index), completed, opportunities));
        }
        return statuses;
    }

    private DailyStatus status(LocalDate date, int completed, int opportunities) {
        DailyStatus status = new DailyStatus();
        status.setStatusDate(date);
        status.setRoutinesDone(completed);
        status.setTotalRoutines(opportunities);
        status.setRoutinesPercentage(opportunities == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(completed * 100L / opportunities));
        status.setTotalWeightRoutines(0);
        status.setTotalBloodPressureRoutines(0);
        status.setTotalFlexibilityRoutines(0);
        status.setTotalMindRoutines(0);
        status.setWeightPercentage(BigDecimal.ZERO);
        status.setBloodPressurePercentage(BigDecimal.ZERO);
        status.setFlexibilityPercentage(BigDecimal.ZERO);
        status.setMindPercentage(BigDecimal.ZERO);
        return status;
    }

    private User user() {
        User user = new User();
        user.setEmail("owner@example.com");
        user.setDisplayName("Owner");
        user.setTypicalCaloriesSaturday(2000);
        user.setTypicalCaloriesSunday(2000);
        user.setTypicalCaloriesMonday(2000);
        user.setTypicalCaloriesTuesday(2000);
        user.setTypicalCaloriesWednesday(2000);
        user.setTypicalCaloriesThursday(2000);
        user.setTypicalCaloriesFriday(2000);
        return user;
    }
}
