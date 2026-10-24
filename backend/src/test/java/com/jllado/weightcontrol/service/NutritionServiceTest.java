package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.api.dto.NutritionDtos.MacroTargetValue;
import com.jllado.weightcontrol.api.dto.NutritionDtos.MacroTargetsResponse;
import com.jllado.weightcontrol.domain.MacroTargetComparison;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NutritionServiceTest {

    @Mock
    private MealRepository repository;

    @Mock
    private WeeklyCaloriePlanService caloriePlans;

    @Mock
    private NutrientTargetService targetService;

    @InjectMocks
    private NutritionService service;

    @Test
    void datedSummariesCarryEachDatesEffectiveMacroCeilings() {
        User user = new User();
        LocalDate first = LocalDate.of(2026, 8, 20);
        LocalDate second = first.plusDays(1);
        when(repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, first, second)).thenReturn(List.of(
            meal(first, 500, "25", "40", "15"), meal(second, 400, "20", "30", "10")));
        when(caloriePlans.effectiveTargets(user, first, second)).thenReturn(List.of(
            new WeeklyCaloriePlanService.EffectiveCalorieTarget(first, 2050, 0),
            new WeeklyCaloriePlanService.EffectiveCalorieTarget(second, 1800, 0)));
        when(targetService.macroTargets(user, 2050)).thenReturn(new MacroTargetsResponse(
            new MacroTargetValue(new BigDecimal("115"), MacroTargetComparison.MINIMUM, null),
            new MacroTargetValue(new BigDecimal("243.75"), MacroTargetComparison.MAXIMUM, null),
            new MacroTargetValue(new BigDecimal("68.33"), MacroTargetComparison.MAXIMUM, null)));
        when(targetService.macroTargets(user, 1800)).thenReturn(new MacroTargetsResponse(
            new MacroTargetValue(new BigDecimal("115"), MacroTargetComparison.MINIMUM, null),
            new MacroTargetValue(new BigDecimal("200.00"), MacroTargetComparison.MAXIMUM, null),
            new MacroTargetValue(new BigDecimal("60.00"), MacroTargetComparison.MAXIMUM, null)));

        var summaries = service.findBetween(user, first, second);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

        assertEquals(68.33, mapper.valueToTree(summaries.getFirst()).at("/macroTargets/fat/value").asDouble());
        assertEquals(60.00, mapper.valueToTree(summaries.getLast()).at("/macroTargets/fat/value").asDouble());
    }

    @Test
    void summariesPreserveZeroCaloriesAndMarkCompleteMacros() {
        User user = new User();
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, date, date)).thenReturn(List.of(
            meal(date, 0, "20", "30", "10"),
            meal(date, 500, "25", "40", "15")
        ));
        stubDailyCalories(user, date, date);

        NutritionService.DailyNutritionSummary summary = service.findBetween(user, date, date).getFirst();

        assertEquals(500, summary.calories());
        assertEquals(new BigDecimal("45"), summary.proteinGrams());
        assertEquals(new BigDecimal("70"), summary.carbohydrateGrams());
        assertEquals(new BigDecimal("25"), summary.fatGrams());
        assertTrue(summary.macrosComplete());
    }

    @Test
    void incompleteSummariesReturnOnlyRecordedPartialTotals() {
        User user = new User();
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, date, date)).thenReturn(List.of(
            meal(date, 500, "25", null, null),
            meal(date, 400, null, null, null)
        ));
        stubDailyCalories(user, date, date);

        NutritionService.DailyNutritionSummary summary = service.findBetween(user, date, date).getFirst();

        assertEquals(new BigDecimal("25"), summary.proteinGrams());
        assertNull(summary.carbohydrateGrams());
        assertNull(summary.fatGrams());
        assertFalse(summary.macrosComplete());
    }

    @Test
    void completionFollowsTheUsersCompletedDashboardDate() {
        User user = new User();
        LocalDate date = LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE).minusDays(2);
        user.setLastCompletedDashboardDate(date.plusDays(1));
        when(repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, date, date.plusDays(2))).thenReturn(List.of(
            meal(date, 57, "20", "30", "10"), meal(date.plusDays(1), 0, null, null, null),
            meal(date.plusDays(2), 0, "20", "30", "10")
        ));
        stubDailyCalories(user, date, date.plusDays(2));

        var summaries = service.findBetween(user, date, date.plusDays(2));

        assertEquals(57, summaries.getFirst().calories());
        assertTrue(summaries.getFirst().completed());
        assertEquals(0, summaries.getLast().calories());
        assertFalse(summaries.getLast().completed());
        assertTrue(summaries.get(1).completed());
        assertEquals(0, summaries.get(1).calories());
        assertFalse(summaries.get(1).macrosComplete());
        assertTrue(summaries.getLast().macrosComplete());
    }

    @Test
    void rangedSummariesRejectFutureAndMoreThanNinetyInclusiveDays() {
        User user = new User();
        LocalDate today = LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE);

        assertThrows(BadRequestException.class, () -> service.findBetween(user, today.minusDays(90), today));
        assertThrows(BadRequestException.class, () -> service.findBetween(user, today, today.plusDays(1)));
    }

    private Meal meal(LocalDate date, int calories, String protein, String carbohydrate, String fat) {
        Meal meal = new Meal();
        meal.setMealDate(date);
        meal.setCalories(calories);
        meal.setProteinGrams(protein == null ? null : new BigDecimal(protein));
        meal.setCarbohydrateGrams(carbohydrate == null ? null : new BigDecimal(carbohydrate));
        meal.setFatGrams(fat == null ? null : new BigDecimal(fat));
        return meal;
    }

    private void stubDailyCalories(User user, LocalDate from, LocalDate to) {
        when(caloriePlans.effectiveTargets(user, from, to)).thenReturn(from.datesUntil(to.plusDays(1))
            .map(date -> new WeeklyCaloriePlanService.EffectiveCalorieTarget(date, 2050, 0)).toList());
    }
}
