package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientSummary;
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
        return new NutrientSummary(total(recorded, FoodPortion::getVitaminDMicrograms),
            total(recorded, FoodPortion::getOmega3Milligrams), total(recorded, FoodPortion::getMagnesiumMilligrams),
            recorded.size(), foods.size(), (int) recorded.stream().filter(food -> Boolean.TRUE.equals(food.getNutrientsEstimated())).count(),
            (int) meals.stream().filter(meal -> meal.getDishes().isEmpty()).count());
    }

    private static BigDecimal total(List<? extends FoodPortion> foods, Function<FoodPortion, BigDecimal> value) {
        return foods.isEmpty() ? null : foods.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
