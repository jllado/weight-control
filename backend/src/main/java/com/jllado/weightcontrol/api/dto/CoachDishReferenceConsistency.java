package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.api.dto.MealDtos.DishReference;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CoachDishReferenceConsistency {
    private CoachDishReferenceConsistency() { }

    public static boolean matches(MealDishRequest dish) {
        DishReference reference = dish.reference();
        if (reference == null || dish.quantity() == null || reference.quantity() == null
            || reference.quantity().signum() <= 0 || reference.calories() == null) return true;
        BigDecimal quantity = dish.quantity();
        BigDecimal referenceQuantity = reference.quantity();
        return dish.calories() != null
            && BigDecimal.valueOf(dish.calories()).compareTo(scale(BigDecimal.valueOf(reference.calories()), quantity, referenceQuantity, 0)) == 0
            && matches(dish.proteinGrams(), reference.proteinGrams(), quantity, referenceQuantity)
            && matches(dish.carbohydrateGrams(), reference.carbohydrateGrams(), quantity, referenceQuantity)
            && matches(dish.fatGrams(), reference.fatGrams(), quantity, referenceQuantity)
            && matches(dish.vitaminDMicrograms(), reference.vitaminDMicrograms(), quantity, referenceQuantity)
            && matches(dish.omega3Milligrams(), reference.omega3Milligrams(), quantity, referenceQuantity)
            && matches(dish.magnesiumMilligrams(), reference.magnesiumMilligrams(), quantity, referenceQuantity)
            && matches(dish.epaDhaMilligrams(), reference.epaDhaMilligrams(), quantity, referenceQuantity)
            && matches(dish.alaMilligrams(), reference.alaMilligrams(), quantity, referenceQuantity)
            && matches(dish.fiberGrams(), reference.fiberGrams(), quantity, referenceQuantity);
    }

    private static boolean matches(BigDecimal consumed, BigDecimal reference, BigDecimal quantity, BigDecimal referenceQuantity) {
        return reference == null ? consumed == null
            : consumed != null && consumed.compareTo(scale(reference, quantity, referenceQuantity, 2)) == 0;
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal quantity, BigDecimal referenceQuantity, int decimals) {
        return value.multiply(quantity).divide(referenceQuantity, decimals, RoundingMode.HALF_UP);
    }
}
