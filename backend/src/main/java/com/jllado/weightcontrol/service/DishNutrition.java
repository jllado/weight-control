package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealDtos.DishReference;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.domain.DishUnit;
import com.jllado.weightcontrol.domain.FoodPortion;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DishNutrition {
    private DishNutrition() { }

    public static void apply(FoodPortion dish, MealDishRequest request) {
        if ((request.quantity() == null) != (request.unit() == null) || (request.reference() != null && request.quantity() == null)) {
            throw new BadRequestException("Dish quantity and unit must be supplied together");
        }
        BigDecimal quantity = request.quantity() == null ? BigDecimal.ONE : request.quantity();
        DishReference reference = request.reference() == null
            ? new DishReference(quantity, request.calories(), request.proteinGrams(), request.carbohydrateGrams(), request.fatGrams(), request.vitaminDMicrograms(), request.omega3Milligrams(), request.magnesiumMilligrams(), request.epaDhaMilligrams(), request.alaMilligrams(), request.fiberGrams())
            : request.reference();
        dish.setQuantity(quantity);
        dish.setUnit(request.unit() == null ? DishUnit.SERVING : request.unit());
        dish.setReferenceQuantity(reference.quantity());
        dish.setReferenceCalories(reference.calories());
        dish.setReferenceProteinGrams(reference.proteinGrams());
        dish.setReferenceCarbohydrateGrams(reference.carbohydrateGrams());
        dish.setReferenceFatGrams(reference.fatGrams());
        dish.setReferenceVitaminDMicrograms(reference.vitaminDMicrograms());
        dish.setReferenceOmega3Milligrams(reference.omega3Milligrams());
        dish.setReferenceMagnesiumMilligrams(reference.magnesiumMilligrams());
        dish.setNutrientSource(request.nutrientSource());
        dish.setNutrientsEstimated(request.nutrientsEstimated());
        dish.setReferenceEpaDhaMilligrams(reference.epaDhaMilligrams());
        dish.setEpaDhaSource(request.epaDhaSource());
        dish.setEpaDhaEstimated(request.epaDhaEstimated());
        dish.setReferenceAlaMilligrams(reference.alaMilligrams());
        dish.setAlaSource(request.alaSource());
        dish.setAlaEstimated(request.alaEstimated());
        dish.setReferenceFiberGrams(reference.fiberGrams());
        dish.setFiberSource(request.fiberSource());
        dish.setFiberEstimated(request.fiberEstimated());
        BigDecimal calories = scale(BigDecimal.valueOf(reference.calories()), quantity, reference.quantity(), 0);
        if (calories.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new BadRequestException("Dish calories exceed the supported range");
        }
        dish.setCalories(calories.intValueExact());
        dish.setProteinGrams(macro(reference.proteinGrams(), quantity, reference.quantity()));
        dish.setCarbohydrateGrams(macro(reference.carbohydrateGrams(), quantity, reference.quantity()));
        dish.setFatGrams(macro(reference.fatGrams(), quantity, reference.quantity()));
        dish.setVitaminDMicrograms(macro(reference.vitaminDMicrograms(), quantity, reference.quantity()));
        dish.setOmega3Milligrams(macro(reference.omega3Milligrams(), quantity, reference.quantity()));
        dish.setMagnesiumMilligrams(macro(reference.magnesiumMilligrams(), quantity, reference.quantity()));
        dish.setEpaDhaMilligrams(macro(reference.epaDhaMilligrams(), quantity, reference.quantity()));
        dish.setAlaMilligrams(macro(reference.alaMilligrams(), quantity, reference.quantity()));
        dish.setFiberGrams(macro(reference.fiberGrams(), quantity, reference.quantity()));
    }

    private static BigDecimal macro(BigDecimal value, BigDecimal quantity, BigDecimal referenceQuantity) {
        if (value == null) return null;
        BigDecimal result = scale(value, quantity, referenceQuantity, 2);
        if (result.compareTo(new BigDecimal("99999999.99")) > 0) throw new BadRequestException("Dish nutrients exceed the supported range");
        return result;
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal quantity, BigDecimal referenceQuantity, int decimals) {
        return value.multiply(quantity).divide(referenceQuantity, decimals, RoundingMode.HALF_UP);
    }
}
