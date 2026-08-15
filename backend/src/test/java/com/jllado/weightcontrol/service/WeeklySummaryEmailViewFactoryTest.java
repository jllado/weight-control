package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.ComparisonStatus;
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

        WeeklySummaryEmailView view = factory.create(user, calculator.progress(user, end, input), "https://weight.example");

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

        WeeklySummaryEmailView view = factory.create(user(), new WeeklyMetrics.Progress(true, current, previous, yearAgo), "https://weight.example");

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

        WeeklySummaryEmailView view = factory.create(user(), new WeeklyMetrics.Progress(true, current, baseline, baseline), "https://weight.example");

        assertEquals(ComparisonStatus.UNCHANGED, view.cardRows().get(1).right().previousComparison().status());
        assertTrue(view.cardRows().get(1).right().previousComparison().displayText().startsWith("→ 0.0 kg"));
        assertEquals(ComparisonStatus.UNCHANGED, view.cardRows().get(2).left().previousComparison().status());
    }

    private List<WeeklySummaryEmailView.Comparison> comparisons(WeeklySummaryEmailView view, boolean previous) {
        return view.cardRows().stream()
            .flatMap(row -> row.right() == null ? java.util.stream.Stream.of(row.left()) : java.util.stream.Stream.of(row.left(), row.right()))
            .map(card -> previous ? card.previousComparison() : card.yearAgoComparison())
            .toList();
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
            new WeeklyMetrics.AverageWeight(new BigDecimal(weight), null, null, 7),
            new WeeklyMetrics.AverageBloodPressure(new BigDecimal(systolic), new BigDecimal(diastolic), 7),
            new BigDecimal(mood),
            7,
            new WeeklyMetrics.AverageSleep(new BigDecimal(sleepSeconds), null, null, null, null, null, 7),
            new WeeklyMetrics.CalorieSummary(7, calorieAverage.multiply(BigDecimal.valueOf(7)).intValue(), calorieAverage, BigDecimal.valueOf(2000), calorieAverage.subtract(BigDecimal.valueOf(2000))),
            new WeeklyMetrics.WorkoutSummary(workouts, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO),
            Map.of(),
            Map.of(),
            new WeeklyMetrics.DecisionMetrics(8, 2, new BigDecimal(decisionWinRate))
        );
    }

    private List<DailyStatus> statuses(LocalDate start, int completed, int opportunities) {
        List<DailyStatus> statuses = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            DailyStatus status = new DailyStatus();
            status.setStatusDate(start.plusDays(index));
            status.setRoutinesDone(completed);
            status.setTotalRoutines(opportunities);
            status.setRoutinesPercentage(BigDecimal.valueOf(completed * 100L / opportunities));
            status.setWeightPercentage(BigDecimal.ZERO);
            status.setBloodPressurePercentage(BigDecimal.ZERO);
            status.setFlexibilityPercentage(BigDecimal.ZERO);
            status.setMindPercentage(BigDecimal.ZERO);
            statuses.add(status);
        }
        return statuses;
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
