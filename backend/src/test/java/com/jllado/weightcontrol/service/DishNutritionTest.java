package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.domain.DishUnit;
import com.jllado.weightcontrol.domain.MealDish;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DishNutritionTest {
    @Test
    void scalesFromReferenceWithoutRoundTripDrift() {
        var dish = new MealDish();
        var reference = new DishReference(new BigDecimal("100"), 101, new BigDecimal("1.01"), null, BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN);
        DishNutrition.apply(dish, request("50", DishUnit.GRAM, reference));
        assertEquals(51, dish.getCalories());
        assertEquals(new BigDecimal("0.51"), dish.getProteinGrams());
        assertNull(dish.getCarbohydrateGrams());
        DishNutrition.apply(dish, request("100", DishUnit.GRAM, DishReference.from(dish)));
        assertEquals(101, dish.getCalories());
        assertEquals(new BigDecimal("1.01"), dish.getProteinGrams());
    }

    @Test
    void defaultsLegacyTotalsToOneServingAndRejectsPartialQuantities() {
        var dish = new MealDish();
        DishNutrition.apply(dish, new MealDishRequest("Rice", 172, null, null, null, null, null, null, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false));
        assertEquals(BigDecimal.ONE, dish.getQuantity());
        assertEquals(DishUnit.SERVING, dish.getUnit());
        assertEquals(172, dish.getReferenceCalories());
        assertThrows(BadRequestException.class, () -> DishNutrition.apply(dish, request("10", null, null)));
        assertThrows(BadRequestException.class, () -> DishNutrition.apply(dish, request(null, DishUnit.GRAM, null)));
    }

    @Test
    void validatesPrecisionPositiveQuantitiesAndCoachReferenceMacros() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String quantity : new String[]{"0", "-1", "1.0001", "100000000"}) {
                assertFalse(validator.validate(request(quantity, DishUnit.GRAM, null)).isEmpty());
            }
            var coach = new CoachMealDishRequest("Rice", 100, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, DishUnit.SERVING, new DishReference(BigDecimal.ONE, 100, null, null, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN), false, null, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
            assertFalse(validator.validate(coach).isEmpty());
        }
    }

    private MealDishRequest request(String quantity, DishUnit unit, DishReference reference) {
        return new MealDishRequest("Rice", 101, new BigDecimal("1.01"), null, BigDecimal.ZERO,
            quantity == null ? null : new BigDecimal(quantity), unit, reference, false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false);
    }
}
