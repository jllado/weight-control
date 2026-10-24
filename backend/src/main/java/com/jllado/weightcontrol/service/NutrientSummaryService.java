package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientSummary;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientCoverage;
import com.jllado.weightcontrol.domain.FoodPortion;
import com.jllado.weightcontrol.domain.Meal;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public final class NutrientSummaryService {
    private NutrientSummaryService() { }

    public static NutrientSummary summarize(List<Meal> meals) {
        var foods = meals.stream().flatMap(meal -> meal.getDishes().stream()).toList();
        var recorded = foods.stream().filter(food -> food.getVitaminDMicrograms() != null
            && food.getOmega3Milligrams() != null && food.getMagnesiumMilligrams() != null).toList();
        return new NutrientSummary(total(foods, FoodPortion::getVitaminDMicrograms),
            total(foods, FoodPortion::getOmega3Milligrams), total(foods, FoodPortion::getMagnesiumMilligrams),
            recorded.size(), foods.size(), (int) recorded.stream().filter(food -> Boolean.TRUE.equals(food.getNutrientsEstimated())).count(),
            (int) meals.stream().filter(meal -> meal.getDishes().isEmpty()).count(),
            total(foods, FoodPortion::getEpaDhaMilligrams),
            coverage(foods, FoodPortion::getVitaminDMicrograms, FoodPortion::getNutrientsEstimated),
            coverage(foods, FoodPortion::getOmega3Milligrams, FoodPortion::getNutrientsEstimated),
            coverage(foods, FoodPortion::getMagnesiumMilligrams, FoodPortion::getNutrientsEstimated),
            coverage(foods, FoodPortion::getEpaDhaMilligrams, FoodPortion::getEpaDhaEstimated),
            total(foods, FoodPortion::getAlaMilligrams),
            coverage(foods, FoodPortion::getAlaMilligrams, FoodPortion::getAlaEstimated),
            total(foods, FoodPortion::getFiberGrams),
            coverage(foods, FoodPortion::getFiberGrams, FoodPortion::getFiberEstimated));
    }

    private static BigDecimal total(List<? extends FoodPortion> foods, Function<FoodPortion, BigDecimal> value) {
        return foods.stream().map(value).filter(java.util.Objects::nonNull).reduce(BigDecimal::add).orElse(null);
    }

    private static NutrientCoverage coverage(List<? extends FoodPortion> foods, Function<FoodPortion, BigDecimal> value,
        Function<FoodPortion, Boolean> estimated) {
        var known = foods.stream().filter(food -> value.apply(food) != null).toList();
        return new NutrientCoverage(known.size(), (int) known.stream().filter(food -> Boolean.TRUE.equals(estimated.apply(food))).count());
    }
}
