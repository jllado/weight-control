package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealDish;
import com.jllado.weightcontrol.domain.MealType;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaturatedFatNutritionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode food() throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"name":"Milk","quantity":50,"unit":"GRAM","calories":32,"fatGrams":2.00,
             "vitaminDMicrograms":0,"magnesiumMilligrams":5,"nutrientSource":"Food label","nutrientsEstimated":false,
             "saturatedFatGrams":1.13,"saturatedFatSource":"Label: 2.25 g per 100 g","saturatedFatEstimated":false,
             "reference":{"quantity":100,"calories":64,"fatGrams":4,"vitaminDMicrograms":0,"magnesiumMilligrams":10,"saturatedFatGrams":2.25}}
            """);
    }

    private MealDish apply(ObjectNode body) throws Exception {
        var food = new MealDish();
        DishNutrition.apply(food, mapper.treeToValue(body, MealDishRequest.class));
        return food;
    }

    @Test void roundsFromReferenceAndRejectsAnImpossibleFatCompositionOnEitherBasis() throws Exception {
        var body = food();
        var milk = apply(body);
        assertEquals(new BigDecimal("1.13"), milk.getSaturatedFatGrams());
        assertEquals(new BigDecimal("2.25"), milk.getReferenceSaturatedFatGrams());
        assertEquals("Label: 2.25 g per 100 g", milk.getSaturatedFatSource());
        assertFalse(milk.getSaturatedFatEstimated());
        assertThrows(BadRequestException.class, () -> apply(body.deepCopy().put("saturatedFatGrams", 2.01)));
        ((ObjectNode) body.get("reference")).put("saturatedFatGrams", 4.01);
        assertThrows(BadRequestException.class, () -> apply(body));
        ((ObjectNode) body.get("reference")).put("saturatedFatGrams", 4);
        assertDoesNotThrow(() -> apply(body.put("saturatedFatGrams", 2)));
        ((ObjectNode) body.get("reference")).putNull("saturatedFatGrams");
        assertThrows(BadRequestException.class, () -> apply(body));
    }

    @Test void referenceOnlyValuesRequireIndependentEvidenceAndUnknownIsDistinctFromZero() throws Exception {
        var body = food().putNull("saturatedFatGrams");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(mapper.treeToValue(body, MealDishRequest.class)).isEmpty());
            body.putNull("saturatedFatSource");
            assertFalse(validator.validate(mapper.treeToValue(body, MealDishRequest.class)).isEmpty());
        }
        var zero = new MealDish();
        zero.setSaturatedFatGrams(BigDecimal.ZERO);
        zero.setSaturatedFatEstimated(true);
        var meal = new Meal();
        meal.getDishes().addAll(List.of(zero, new MealDish()));
        var summary = NutrientSummaryService.summarize(List.of(meal, new Meal()));
        assertEquals(BigDecimal.ZERO, summary.saturatedFatGrams());
        assertEquals(1, summary.saturatedFatCoverage().estimatedFoods());
        assertEquals(1, summary.saturatedFatCoverage().foodsWithValues());
        assertEquals(2, summary.totalFoods());
        assertEquals(1, summary.mealsWithoutFoods());
        assertNull(NutrientSummaryService.summarize(List.of(new Meal())).saturatedFatGrams());
    }

    @Test void partialFoodAndUnitemizedSubtotalsKeepDistinctCoverageAndSources() throws Exception {
        var itemized = new Meal();
        itemized.getDishes().add(apply(food()));
        itemized.getDishes().add(new MealDish());
        var unitemized = new Meal();
        unitemized.setMealType(MealType.SNACK);
        unitemized.setMealSequence(2);
        unitemized.setEstimatedSaturatedFatGrams(new BigDecimal("3.20"));
        unitemized.setSaturatedFatEstimateSource("Model based on the recorded fat mix");
        var summary = NutrientSummaryService.summarize(List.of(itemized, unitemized, new Meal()));
        assertEquals(new BigDecimal("4.33"), summary.saturatedFatGrams());
        assertEquals(2, summary.totalFoods());
        assertEquals(1, summary.saturatedFatCoverage().foodsWithValues());
        assertEquals(0, summary.saturatedFatCoverage().estimatedFoods());
        assertEquals(2, summary.mealsWithoutFoods());
        var estimate = summary.unitemizedSaturatedFatEstimates().getFirst();
        assertEquals(MealType.SNACK, estimate.mealType());
        assertEquals(2, estimate.mealSequence());
        assertEquals(new BigDecimal("3.20"), estimate.grams());
        assertEquals(unitemized.getSaturatedFatEstimateSource(), estimate.source());
    }
}
