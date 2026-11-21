package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.api.dto.NutritionDtos.*;
import com.jllado.weightcontrol.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyNutritionSnapshotServiceTest {
    @Test void transitionCapturesOnlyItsTwoDatedNutritionTargets() {
        var user = new User();
        var monday = LocalDate.parse("2026-11-23");
        user.getPlanningWeekChanges().add(new PlanningWeekChange(user, monday));
        var nutrition = mock(NutritionService.class);
        var plans = mock(WeeklyCaloriePlanService.class);
        var targets = new NutrientTargetService(mock(UserRepository.class), plans);
        user.setLastCompletedDashboardDate(monday);
        var periods = WeeklyMetricsCalculator.comparisonPeriods(user, monday.minusDays(1));
        var snapshot = new WeeklyNutritionSnapshotService(nutrition, targets).capture(user, periods, budgets(periods, date -> 2000));
        assertEquals(monday.minusDays(2), snapshot.current().startDate());
        assertEquals(monday.minusDays(1), snapshot.current().endDate());
        assertEquals(2, snapshot.current().completedDays());
        assertEquals(List.of(monday.minusDays(2), monday.minusDays(1)), snapshot.current().datedTargets().stream().map(WeeklyNutritionSnapshot.DatedTarget::date).toList());
    }

    @Test void snapshotFreezesThreePeriodsWithIndependentCoverageAndExactDatedTargets() {
        var nutrition = mock(NutritionService.class);
        var plans = mock(WeeklyCaloriePlanService.class);
        var targets = new NutrientTargetService(mock(UserRepository.class), plans);
        var user = new User(); user.setBirthDate(LocalDate.of(1980,1,1)); user.setSex(UserSex.MALE);
        user.setMacroProteinTargetGrams(new BigDecimal("115")); user.setMacroFatTargetPercentage(new BigDecimal("30"));
        user.setNutrientEpaDhaTargetMilligrams(new BigDecimal("250"));
        var friday = LocalDate.of(2026,10,23);
        user.setLastCompletedDashboardDate(friday);
        var periods = WeeklyMetricsCalculator.comparisonPeriods(user, friday);
        var calorieTargets = budgets(periods, date -> date.equals(friday) ? 2589 : 2050);
        var coverage = new NutrientCoverage(2, 1);
        var partial = new NutrientCoverage(1, 1);
        var first = new NutrientSummary(BigDecimal.ONE, new BigDecimal("900"), new BigDecimal("200"), 2, 2, 1, 0,
            new BigDecimal("250"), coverage, coverage, coverage, coverage, null, new NutrientCoverage(0,0),
            new BigDecimal("25"), coverage, new BigDecimal("4.99"), coverage);
        var second = new NutrientSummary(BigDecimal.ONE, new BigDecimal("900"), new BigDecimal("200"), 2, 2, 1, 0,
            new BigDecimal("150"), coverage, coverage, coverage, partial, null, new NutrientCoverage(0,0),
            new BigDecimal("25"), coverage, new BigDecimal("9"), partial);
        when(nutrition.findBetween(eq(user), eq(friday.minusDays(6)), eq(friday), anyMap())).thenReturn(List.of(
            new NutritionService.DailyNutritionSummary(friday.minusDays(1), 2050, new BigDecimal("120.00"), new BigDecimal("200"), new BigDecimal("60"), true, first, true, null),
            new NutritionService.DailyNutritionSummary(friday, 2589, new BigDecimal("120.01"), new BigDecimal("200"), new BigDecimal("60"), true, second, true, null)));
        var snapshot = new WeeklyNutritionSnapshotService(nutrition, targets).capture(user, periods, calorieTargets);
        assertEquals("Current targets captured when saved", snapshot.targetLabel());
        assertEquals(3, List.of(snapshot.current(), snapshot.previous(), snapshot.yearAgo()).size());
        var salt = metric(snapshot.current(), "salt");
        assertEquals(new BigDecimal("4.99"), salt.average());
        assertEquals(1, salt.eligibleDays());
        assertEquals(3, salt.coveredFoods());
        assertEquals(new BigDecimal("5"), salt.target());
        assertEquals(MacroTargetComparison.STRICT_MAXIMUM, salt.comparison());
        user.setNutrientSaltTargetGrams(new BigDecimal("3"));
        assertEquals(new BigDecimal("5"), snapshot.targets().salt().value());
        assertEquals("GOAL_MET", NutrientAssessment.compare(salt.valueForAssessment(), salt.targetForAssessment(), salt.comparison(), true).code());
        var protein = metric(snapshot.current(), "protein");
        assertEquals(new BigDecimal("120.01"), protein.average());
        assertEquals(0, new BigDecimal("120.005").compareTo(protein.assessmentAverage()));
        assertEquals(2, protein.eligibleDays());
        assertEquals(new BigDecimal("290.91"), metric(snapshot.current(), "carbohydrate").target());
        assertEquals(new BigDecimal("77.32"), metric(snapshot.current(), "fat").target());
        assertEquals(1, metric(snapshot.current(), "epaDha").eligibleDays());
        assertEquals(new BigDecimal("250.00"), metric(snapshot.current(), "epaDha").average());
        assertEquals(4, metric(snapshot.current(), "epaDha").totalFoods());
        assertNull(metric(snapshot.current(), "ala").average());
        assertNull(metric(snapshot.previous(), "protein").average());
        assertEquals(7, snapshot.current().datedTargets().size());
        user.setMacroProteinTargetGrams(new BigDecimal("500"));
        assertEquals(new BigDecimal("115"), protein.target().stripTrailingZeros());
        assertEquals(new BigDecimal("338.075"), snapshot.current().datedTargets().getLast().macroTargets().carbohydrate().value());
        calorieTargets = budgets(periods, date -> date.equals(friday.minusDays(3)) ? 1800 : 1801);
        when(nutrition.findBetween(eq(user), eq(friday.minusDays(6)), eq(friday), anyMap())).thenReturn(friday.minusDays(3).datesUntil(friday.plusDays(1))
            .map(date -> new NutritionService.DailyNutritionSummary(date, 1800, new BigDecimal("115"), new BigDecimal("200"),
                new BigDecimal("60"), true, first, true, null)).toList());
        assertEquals(new BigDecimal("60.03"), metric(new WeeklyNutritionSnapshotService(nutrition, targets).capture(user, periods, calorieTargets).current(), "fat").target());
        user.setMacroProteinTargetGrams(new BigDecimal("115"));
        calorieTargets = budgets(periods, date -> date.equals(friday) || date.equals(friday.minusDays(2)) ? 2593 : 2589);
        var boundary = new WeeklyNutritionSnapshotService(nutrition, targets).capture(user, periods, calorieTargets);
        assertEquals(new BigDecimal("338.43"), metric(boundary.current(), "carbohydrate").target());
        assertEquals(new BigDecimal("30"), boundary.current().datedTargets().getLast().fatPercentage());
        assertEquals(new BigDecimal("338.08"), targets.macroTargets(user, 2589).carbohydrate().value());
    }

    private java.util.Map<LocalDate, Integer> budgets(WeeklyMetricsCalculator.ComparisonPeriods periods, java.util.function.Function<LocalDate, Integer> calories) {
        return periods.yearAgo().start().datesUntil(periods.current().end().plusDays(1))
            .collect(java.util.stream.Collectors.toMap(java.util.function.Function.identity(), calories));
    }

    private WeeklyNutritionSnapshot.Measure metric(WeeklyNutritionSnapshot.Period period, String key) {
        return period.measures().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
    }
}
