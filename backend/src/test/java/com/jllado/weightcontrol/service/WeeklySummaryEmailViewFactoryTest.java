package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
        assertEquals("+25.0 pp vs last week", view.previousRoutineComparison());
        assertEquals("+50.0 pp vs 52 weeks ago", view.yearAgoRoutineComparison());
        assertEquals(7, view.days().size());
        assertEquals("Not recorded", view.cardRows().get(0).left().value());
        assertTrue(view.subject().contains("8 Aug–14 Aug"));
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
