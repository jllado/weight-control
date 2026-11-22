package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealDtos.DishReference;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.domain.CatalogFood;
import com.jllado.weightcontrol.domain.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Supplies saturated fat when the version-three Coach cannot submit it. Comparable fractions use
 * <a href="https://www.canada.ca/en/health-canada/services/food-nutrition/healthy-eating/nutrient-data/nutrient-value-some-common-foods-2008.html">Health Canada's food composition tables (2, 6–10 and 14)</a>.
 * The mixed-diet fallback divides the two adult dietary energy shares reported by
 * <a href="https://www.ars.usda.gov/research/publications/publication/?seqNo115=383709">Bowman et al., WWEIA/NHANES 2017–18</a>.
 */
final class LegacyCoachSaturatedFat {
    private LegacyCoachSaturatedFat() { }

    static List<MealDishRequest> complete(User owner, Integer version, List<MealDishRequest> foods, CatalogFoodService catalog) {
        var result = Integer.valueOf(3).equals(version)
            ? foods.stream().map(food -> enrich(owner, food, catalog)).toList() : foods;
        CoachFoodNutrition.requireComplete(result);
        return result;
    }

    private static MealDishRequest enrich(User owner, MealDishRequest food, CatalogFoodService catalog) {
        var ref = food.reference();
        if (!CoachFoodNutrition.omitsSaturatedFat(food) || ref.fatGrams() == null || food.quantity() == null
            || ref.quantity() == null || ref.quantity().signum() <= 0 || ref.calories() == null) return food;
        var saved = catalog.findActiveByName(owner, food.name()).filter(item -> aligned(item, food));
        if (saved.isPresent()) {
            var item = saved.get();
            var grams = scale(item.getReferenceSaturatedFatGrams(), ref.quantity(), item.getReferenceQuantity(), 2);
            return withEstimate(food, grams, item.getSaturatedFatSource(), item.getSaturatedFatEstimated());
        }
        if (ref.fatGrams().signum() == 0) return withEstimate(food, BigDecimal.ZERO, "Inferred from zero recorded total fat", true);
        var profile = profile(food.name());
        var grams = ref.fatGrams().multiply(profile.saturated()).divide(profile.fat(), 2, RoundingMode.HALF_UP);
        return withEstimate(food, grams, profile.source(), true);
    }

    private static MealDishRequest withEstimate(MealDishRequest food, BigDecimal refGrams, String source, boolean estimated) {
        var consumed = scale(refGrams, food.quantity(), food.reference().quantity(), 2);
        return food.withSaturatedFat(consumed, refGrams, source, estimated);
    }

    private static boolean aligned(CatalogFood saved, MealDishRequest food) {
        DishReference ref = food.reference();
        return saved.getUnit() == food.unit() && saved.getReferenceSaturatedFatGrams() != null
            && saved.getSaturatedFatEstimated() != null && saved.getSaturatedFatSource() != null && !saved.getSaturatedFatSource().isBlank()
            && matches(BigDecimal.valueOf(saved.getReferenceCalories()), BigDecimal.valueOf(ref.calories()), saved, ref, 0)
            && matches(saved.getReferenceProteinGrams(), ref.proteinGrams(), saved, ref, 2)
            && matches(saved.getReferenceCarbohydrateGrams(), ref.carbohydrateGrams(), saved, ref, 2)
            && matches(saved.getReferenceFatGrams(), ref.fatGrams(), saved, ref, 2);
    }

    private static boolean matches(BigDecimal savedValue, BigDecimal requested, CatalogFood saved, DishReference ref, int decimals) {
        return savedValue != null && requested != null
            && scale(savedValue, ref.quantity(), saved.getReferenceQuantity(), decimals).compareTo(requested) == 0;
    }

    private static BigDecimal scale(BigDecimal grams, BigDecimal to, BigDecimal from, int decimals) {
        return grams.multiply(to).divide(from, decimals, RoundingMode.HALF_UP);
    }

    private record FatProfile(BigDecimal saturated, BigDecimal fat, String source) {
        static FatProfile comparable(String name, String saturated, String fat) {
            return new FatProfile(new BigDecimal(saturated), new BigDecimal(fat),
                "Estimated from Health Canada, Nutrient Value of Some Common Foods (2008), " + name + " (" + saturated + "/" + fat + " g saturated/total fat)");
        }
    }

    // Match whole names so, for example, a salmon sandwich never inherits the profile for plain salmon.
    private static FatProfile profile(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "olive oil", "extra virgin olive oil", "extra-virgin olive oil", "aceite de oliva", "aceite de oliva virgen extra", "aove"
                -> FatProfile.comparable("olive oil", "1.8", "14");
            case "canola oil", "rapeseed oil", "aceite de colza" -> FatProfile.comparable("canola oil", "1", "14");
            case "sunflower oil", "aceite de girasol" -> FatProfile.comparable("sunflower oil", "1.4", "14");
            case "butter", "salted butter", "unsalted butter", "mantequilla" -> FatProfile.comparable("butter", "2.5", "4");
            case "cheddar", "cheddar cheese", "queso cheddar" -> FatProfile.comparable("cheddar", "10.5", "17");
            case "whole milk", "leche entera" -> FatProfile.comparable("whole milk", "5.4", "8");
            case "oats", "oatmeal", "rolled oats", "avena", "copos de avena" -> FatProfile.comparable("plain cooked oatmeal", "0.3", "2");
            case "walnuts", "nueces" -> FatProfile.comparable("dried walnuts", "1.6", "17");
            case "almonds", "raw almonds", "almendras" -> FatProfile.comparable("dried almonds", "1.4", "18");
            case "egg", "eggs", "huevo", "huevos", "boiled egg", "boiled eggs", "hard-boiled egg", "hard boiled egg", "huevo cocido", "huevos cocidos"
                -> FatProfile.comparable("hard-boiled egg", "1.6", "5");
            case "fried egg", "fried eggs", "huevo frito", "huevos fritos" -> FatProfile.comparable("fried eggs", "3.6", "14");
            case "salmon", "baked salmon", "grilled salmon", "salmón", "salmón al horno", "salmón a la plancha"
                -> FatProfile.comparable("baked Atlantic farmed salmon", "1.9", "9");
            case "chicken breast", "roasted chicken breast", "grilled chicken breast", "skinless chicken breast", "pechuga de pollo", "pechuga de pollo asada"
                -> FatProfile.comparable("roasted chicken breast without skin", "0.4", "2");
            default -> new FatProfile(new BigDecimal("11.7"), new BigDecimal("36.2"),
                "Approximate mixed-diet proxy for unidentified fat composition: USDA WWEIA/NHANES 2017–18 adult dietary mix (11.7/36.2 saturated/total fat energy share); actual ingredients may differ");
        };
    }
}
