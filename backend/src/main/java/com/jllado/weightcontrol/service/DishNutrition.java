package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealDtos.DishReference;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.domain.DishUnit;
import com.jllado.weightcontrol.domain.MealDish;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DishNutrition {
    private DishNutrition() { }

    public static void apply(MealDish dish, MealDishRequest request) {
        if ((request.quantity() == null) != (request.unit() == null) || (request.reference() != null && request.quantity() == null)) {
            throw new BadRequestException("Dish quantity and unit must be supplied together");
        }
        BigDecimal quantity = request.quantity() == null ? BigDecimal.ONE : request.quantity();
        DishReference reference = request.reference() == null
            ? new DishReference(quantity, request.calories(), request.proteinGrams(), request.carbohydrateGrams(), request.fatGrams())
            : request.reference();
        dish.setQuantity(quantity);
        dish.setUnit(request.unit() == null ? DishUnit.SERVING : request.unit());
        dish.setReferenceQuantity(reference.quantity());
        dish.setReferenceCalories(reference.calories());
        dish.setReferenceProteinGrams(reference.proteinGrams());
        dish.setReferenceCarbohydrateGrams(reference.carbohydrateGrams());
        dish.setReferenceFatGrams(reference.fatGrams());
        BigDecimal calories = scale(BigDecimal.valueOf(reference.calories()), quantity, reference.quantity(), 0);
        if (calories.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new BadRequestException("Dish calories exceed the supported range");
        }
        dish.setCalories(calories.intValueExact());
        dish.setProteinGrams(macro(reference.proteinGrams(), quantity, reference.quantity()));
        dish.setCarbohydrateGrams(macro(reference.carbohydrateGrams(), quantity, reference.quantity()));
        dish.setFatGrams(macro(reference.fatGrams(), quantity, reference.quantity()));
    }

    private static BigDecimal macro(BigDecimal value, BigDecimal quantity, BigDecimal referenceQuantity) {
        if (value == null) return null;
        BigDecimal result = scale(value, quantity, referenceQuantity, 2);
        if (result.compareTo(new BigDecimal("99999999.99")) > 0) throw new BadRequestException("Dish macros exceed the supported range");
        return result;
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal quantity, BigDecimal referenceQuantity, int decimals) {
        return value.multiply(quantity).divide(referenceQuantity, decimals, RoundingMode.HALF_UP);
    }
}
