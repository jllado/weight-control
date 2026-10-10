package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.Meal;
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

    @InjectMocks
    private NutritionService service;

    @Test
    void summariesPreserveZeroCaloriesAndMarkCompleteMacros() {
        User user = new User();
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, date, date)).thenReturn(List.of(
            meal(date, 0, "20", "30", "10"),
            meal(date, 500, "25", "40", "15")
        ));

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
}
