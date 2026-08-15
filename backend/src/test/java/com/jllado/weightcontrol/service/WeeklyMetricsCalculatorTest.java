package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyMetricsCalculatorTest {

    private final WeeklyMetricsCalculator calculator = new WeeklyMetricsCalculator();

    @Test
    void completeWeekUsesOpportunityWeightedCompletionAndRecordedDayAverages() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        List<DailyStatus> statuses = statuses(end.minusDays(6), new int[][]{
            {2, 3}, {1, 3}, {3, 3}, {0, 3}, {2, 3}, {3, 3}, {3, 3}
        });
        Mood morning = mood(end.minusDays(1), MoodPeriod.MORNING, 1);
        Mood evening = mood(end.minusDays(1), MoodPeriod.EVENING, 5);
        Mood friday = mood(end, MoodPeriod.MORNING, 5);
        WeeklyMetricsCalculator.Input input = new WeeklyMetricsCalculator.Input(
            statuses,
            List.of(weight(user, end, "68.20"), weight(user, end, "68.80")),
            List.of(),
            List.of(morning, evening, friday),
            List.of(),
            List.of(
                new CalorieService.DailyCalories(end.minusDays(1), 0),
                new CalorieService.DailyCalories(end, 2000)
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );

        WeeklyMetrics.Summary summary = calculator.progress(user, end, input).currentPeriod();

        assertEquals(14, summary.routineCompletion().completed());
        assertEquals(21, summary.routineCompletion().opportunities());
        assertEquals(0, new BigDecimal("66.67").compareTo(summary.routineCompletion().percentage()));
        assertEquals(7, summary.routineCompletion().days().size());
        assertEquals(2, summary.calories().entryCount());
        assertEquals(0, new BigDecimal("1000.00").compareTo(summary.calories().averageCalories()));
        assertEquals(2, summary.moodDayCount());
        assertEquals(0, new BigDecimal("4.00").compareTo(summary.moodAverage()));
        assertEquals(2, summary.weight().measurementCount());
        assertEquals(0, new BigDecimal("68.50").compareTo(summary.weight().weightKg()));
    }

    @Test
    void comparisonsUsePreviousAndWeekdayAlignedYearAgoPeriods() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        WeeklyMetrics.Progress progress = calculator.progress(user, end, emptyInput());

        assertEquals(LocalDate.of(2026, 8, 8), progress.currentPeriod().startDate());
        assertEquals(LocalDate.of(2026, 8, 14), progress.currentPeriod().endDate());
        assertEquals(LocalDate.of(2026, 8, 1), progress.previousComparablePeriod().startDate());
        assertEquals(LocalDate.of(2026, 8, 7), progress.previousComparablePeriod().endDate());
        assertEquals(LocalDate.of(2025, 8, 9), progress.yearAgoComparablePeriod().startDate());
        assertEquals(LocalDate.of(2025, 8, 15), progress.yearAgoComparablePeriod().endDate());
        assertNull(progress.currentPeriod().routineCompletion().percentage());
    }

    private WeeklyMetricsCalculator.Input emptyInput() {
        return new WeeklyMetricsCalculator.Input(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private List<DailyStatus> statuses(LocalDate start, int[][] values) {
        List<DailyStatus> statuses = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            DailyStatus status = new DailyStatus();
            status.setStatusDate(start.plusDays(index));
            status.setRoutinesDone(values[index][0]);
            status.setTotalRoutines(values[index][1]);
            status.setRoutinesPercentage(BigDecimal.valueOf(values[index][0] * 100L).divide(BigDecimal.valueOf(values[index][1]), 2, java.math.RoundingMode.HALF_UP));
            status.setWeightPercentage(BigDecimal.ZERO);
            status.setBloodPressurePercentage(BigDecimal.ZERO);
            status.setFlexibilityPercentage(BigDecimal.ZERO);
            status.setMindPercentage(BigDecimal.ZERO);
            statuses.add(status);
        }
        return statuses;
    }

    private Mood mood(LocalDate date, MoodPeriod period, int value) {
        Mood mood = new Mood();
        mood.setMoodDate(date);
        mood.setPeriod(period);
        mood.setValue(value);
        return mood;
    }

    private Weight weight(User user, LocalDate date, String value) {
        Weight weight = new Weight();
        weight.setUser(user);
        weight.setMeasuredAt(DateTimes.startOfDay(date).plusHours(8));
        weight.setWeight(new BigDecimal(value));
        weight.setFatPercentage(new BigDecimal("20.00"));
        weight.setMusclePercentage(new BigDecimal("40.00"));
        return weight;
    }

    private User user() {
        User user = new User();
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
