package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.domain.*;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NutrientSummaryTest {
    @Test void scalesNutrientsFromStableReferencesAndPreservesSource() {
        var food = new MealDish();
        var reference = new DishReference(new BigDecimal("100"), 100, null, null, null,
            new BigDecimal("2.01"), new BigDecimal("100.01"), new BigDecimal("20.01"));
        DishNutrition.apply(food, new MealDishRequest("Fish", 50, null, null, null,
            new BigDecimal("50"), DishUnit.GRAM, reference, false, BigDecimal.ONE, BigDecimal.ONE,
            BigDecimal.ONE, "Published composition; portion estimate", true));
        assertEquals(new BigDecimal("1.01"), food.getVitaminDMicrograms());
        assertEquals(new BigDecimal("50.01"), food.getOmega3Milligrams());
        assertEquals(new BigDecimal("10.01"), food.getMagnesiumMilligrams());
        assertEquals(new BigDecimal("2.01"), DishReference.from(food).vitaminDMicrograms());
        assertEquals("Published composition; portion estimate", food.getNutrientSource());
        assertTrue(food.getNutrientsEstimated());
    }

    @Test void distinguishesRecordedZeroEstimatesUnknownFoodsAndFoodlessMeals() {
        var recorded = new MealDish();
        recorded.setVitaminDMicrograms(BigDecimal.ZERO);
        recorded.setOmega3Milligrams(new BigDecimal("125.25"));
        recorded.setMagnesiumMilligrams(new BigDecimal("30.10"));
        recorded.setNutrientsEstimated(true);
        var meal = new Meal();
        meal.getDishes().add(recorded);
        meal.getDishes().add(new MealDish());
        var summary = NutrientSummaryService.summarize(List.of(meal, new Meal()));
        assertEquals(BigDecimal.ZERO, summary.vitaminDMicrograms());
        assertEquals(new BigDecimal("125.25"), summary.omega3Milligrams());
        assertEquals(1, summary.foodsWithValues());
        assertEquals(2, summary.totalFoods());
        assertEquals(1, summary.estimatedFoods());
        assertEquals(1, summary.mealsWithoutFoods());
        assertNull(NutrientSummaryService.summarize(List.of(new Meal())).vitaminDMicrograms());
        assertNull(NutrientSummaryService.summarize(List.of()).omega3Milligrams());
    }

    @Test void requiresAllNutrientsAndProvenanceAtWriteBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var missing = new MealDishRequest("Food", 100, null, null, null);
            assertEquals(5, validator.validate(missing).size());
            var complete = new MealDishRequest("Food", 100, null, null, null, BigDecimal.ONE,
                DishUnit.SERVING, null, false, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN, "Label", false);
            assertTrue(validator.validate(complete).isEmpty());
        }
    }
}
