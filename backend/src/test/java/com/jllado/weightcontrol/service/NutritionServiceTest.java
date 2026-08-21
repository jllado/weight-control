package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
