package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.CoachDishReferenceConsistency;
import com.jllado.weightcontrol.api.dto.MealDtos.CoachMealDishRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.DishReference;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.domain.FoodPortion;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

final class CoachFoodNutrition {
    private CoachFoodNutrition() { }

    static void requireComplete(List<MealDishRequest> foods) {
        for (var food : foods) {
            var reference = food.reference();
            if (food.quantity() == null || food.unit() == null || reference == null
                || food.proteinGrams() == null || reference.proteinGrams() == null
                || food.carbohydrateGrams() == null || reference.carbohydrateGrams() == null
                || food.fatGrams() == null || reference.fatGrams() == null
                || food.vitaminDMicrograms() == null || reference.vitaminDMicrograms() == null
                || food.magnesiumMilligrams() == null || reference.magnesiumMilligrams() == null
                || food.omega3Milligrams() == null || reference.omega3Milligrams() == null
                || food.epaDhaMilligrams() == null || reference.epaDhaMilligrams() == null
                || food.alaMilligrams() == null || reference.alaMilligrams() == null
                || food.fiberGrams() == null || reference.fiberGrams() == null
                || blank(food.nutrientSource()) || food.nutrientsEstimated() == null
                || blank(food.epaDhaSource()) || food.epaDhaEstimated() == null
                || blank(food.alaSource()) || food.alaEstimated() == null
                || blank(food.fiberSource()) || food.fiberEstimated() == null
                || !CoachDishReferenceConsistency.matches(food)) {
                throw new BadRequestException("New or changed Coach foods require sourced values or labeled estimates and matching references for every nutrient");
            }
        }
    }

    static boolean sameOrderedMealFoods(List<? extends FoodPortion> previous, List<CoachMealDishRequest> next) {
        if (previous.size() != next.size()) return false;
        for (int index = 0; index < previous.size(); index++) {
            var food = next.get(index);
            if (food.addToCatalog() || !sameSnapshot(previous.get(index), food.meal(), food.fruit())) return false;
        }
        return true;
    }

    static boolean sameOrderedRecipeFoods(List<? extends FoodPortion> previous, List<MealDishRequest> next) {
        if (previous.size() != next.size()) return false;
        for (int index = 0; index < previous.size(); index++) {
            var food = next.get(index);
            if (!sameSnapshot(previous.get(index), food, food.fruit())) return false;
        }
        return true;
    }

    private static boolean sameSnapshot(FoodPortion old, MealDishRequest next, Boolean requestedFruit) {
        DishReference ref = next.reference();
        return ref != null && old.getName().equals(next.name())
            && (requestedFruit == null || old.isFruit() == requestedFruit)
            && old.getUnit() == next.unit() && same(old.getQuantity(), next.quantity())
            && Objects.equals(old.getCalories(), next.calories())
            && same(old.getProteinGrams(), next.proteinGrams())
            && same(old.getCarbohydrateGrams(), next.carbohydrateGrams())
            && same(old.getFatGrams(), next.fatGrams())
            && same(old.getVitaminDMicrograms(), next.vitaminDMicrograms())
            && same(old.getOmega3Milligrams(), next.omega3Milligrams())
            && same(old.getMagnesiumMilligrams(), next.magnesiumMilligrams())
            && same(old.getEpaDhaMilligrams(), next.epaDhaMilligrams())
            && same(old.getAlaMilligrams(), next.alaMilligrams())
            && same(old.getFiberGrams(), next.fiberGrams())
            && Objects.equals(old.getNutrientSource(), next.nutrientSource())
            && Objects.equals(old.getNutrientsEstimated(), next.nutrientsEstimated())
            && Objects.equals(old.getEpaDhaSource(), next.epaDhaSource())
            && Objects.equals(old.getEpaDhaEstimated(), next.epaDhaEstimated())
            && Objects.equals(old.getAlaSource(), next.alaSource())
            && Objects.equals(old.getAlaEstimated(), next.alaEstimated())
            && Objects.equals(old.getFiberSource(), next.fiberSource())
            && Objects.equals(old.getFiberEstimated(), next.fiberEstimated())
            && same(old.getReferenceQuantity(), ref.quantity())
            && Objects.equals(old.getReferenceCalories(), ref.calories())
            && same(old.getReferenceProteinGrams(), ref.proteinGrams())
            && same(old.getReferenceCarbohydrateGrams(), ref.carbohydrateGrams())
            && same(old.getReferenceFatGrams(), ref.fatGrams())
            && same(old.getReferenceVitaminDMicrograms(), ref.vitaminDMicrograms())
            && same(old.getReferenceOmega3Milligrams(), ref.omega3Milligrams())
            && same(old.getReferenceMagnesiumMilligrams(), ref.magnesiumMilligrams())
            && same(old.getReferenceEpaDhaMilligrams(), ref.epaDhaMilligrams())
            && same(old.getReferenceAlaMilligrams(), ref.alaMilligrams())
            && same(old.getReferenceFiberGrams(), ref.fiberGrams());
    }

    private static boolean same(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
