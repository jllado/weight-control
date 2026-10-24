package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.domain.*;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NutrientSummaryTest {
    private final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test void scalesEpaDhaIndependentlyAndRoundTripsItsEvidence() throws Exception {
        var request = mapper.readValue("""
            {"name":"Fish","calories":50,"quantity":50,"unit":"GRAM",
             "vitaminDMicrograms":1,"omega3Milligrams":900,"magnesiumMilligrams":10,
             "nutrientSource":"Old total source","nutrientsEstimated":false,
             "epaDhaMilligrams":20,"epaDhaSource":"EPA+DHA composition table","epaDhaEstimated":true,
             "alaMilligrams":5,"alaSource":"ALA composition table","alaEstimated":false,
             "fiberGrams":0,"fiberSource":"Fiber composition table","fiberEstimated":false,
             "reference":{"quantity":100,"calories":100,"vitaminDMicrograms":2,
               "omega3Milligrams":1800,"magnesiumMilligrams":20,"epaDhaMilligrams":123.45,
               "alaMilligrams":10,"fiberGrams":0}}
            """, MealDishRequest.class);
        var food = new MealDish();
        food.setPosition(1);
        DishNutrition.apply(food, request);
        var response = mapper.valueToTree(MealDishResponse.from(food));
        assertEquals(61.73, response.path("epaDhaMilligrams").asDouble());
        assertEquals(123.45, response.path("reference").path("epaDhaMilligrams").asDouble());
        assertEquals("EPA+DHA composition table", response.path("epaDhaSource").asText());
        assertTrue(response.path("epaDhaEstimated").asBoolean());
        assertEquals(5.00, response.path("alaMilligrams").asDouble());
        assertEquals(10.00, response.path("reference").path("alaMilligrams").asDouble());
        assertEquals("ALA composition table", response.path("alaSource").asText());
        assertEquals(0.00, response.path("fiberGrams").asDouble());
        assertEquals(0.00, response.path("reference").path("fiberGrams").asDouble());
        assertEquals("Fiber composition table", response.path("fiberSource").asText());
        assertEquals(new BigDecimal("900.00"), food.getOmega3Milligrams());
        assertEquals("Old total source", food.getNutrientSource());
    }

    @Test void reportsCoverageAndSumsForEachNutrientIndependently() {
        var food = new MealDish();
        food.setVitaminDMicrograms(BigDecimal.ZERO);
        food.setMagnesiumMilligrams(BigDecimal.TEN);
        food.setNutrientsEstimated(true);
        var meal = new Meal();
        meal.getDishes().add(food);
        var summary = NutrientSummaryService.summarize(List.of(meal));
        assertEquals(BigDecimal.ZERO, summary.vitaminDMicrograms());
        assertEquals(BigDecimal.TEN, summary.magnesiumMilligrams());
        assertNull(summary.omega3Milligrams());
        assertEquals(0, summary.foodsWithValues());
        var json = mapper.valueToTree(summary);
        assertEquals(1, json.path("vitaminDCoverage").path("foodsWithValues").asInt());
        assertEquals(1, json.path("magnesiumCoverage").path("estimatedFoods").asInt());
        assertEquals(0, json.path("epaDhaCoverage").path("foodsWithValues").asInt());
        assertTrue(json.path("epaDhaMilligrams").isNull());
        assertEquals(0, json.path("alaCoverage").path("foodsWithValues").asInt());
        assertTrue(json.path("alaMilligrams").isNull());
        assertEquals(0, json.path("fiberCoverage").path("foodsWithValues").asInt());
        assertTrue(json.path("fiberGrams").isNull());
    }

    @Test void allowsUnknownTotalOmegaAndEpaWithoutHidingOtherNutrients() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new MealDishRequest("Food", 100, null, null, null, BigDecimal.ONE,
                DishUnit.SERVING, null, false, BigDecimal.ZERO, null, BigDecimal.TEN, "Label", false);
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
    }
    @Test void recordedNewNutrientsRequireTheirOwnEvidence() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (String field : List.of("epaDha", "ala", "fiber")) {
                String amount = field.equals("fiber") ? "fiberGrams" : field + "Milligrams";
                var request = mapper.readValue("""
                    {"name":"Food","calories":100,"quantity":1,"unit":"SERVING",
                     "vitaminDMicrograms":0,"magnesiumMilligrams":0,
                     "nutrientSource":"Label","nutrientsEstimated":false,"%s":1,"nutrientSchemaVersion":2}
                    """.formatted(amount), MealDishRequest.class);
                assertFalse(factory.getValidator().validate(request).isEmpty(), field);
            }
        }
    }
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

    @Test void requiresVitaminDMagnesiumAndTheirProvenanceAtWriteBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var missing = new MealDishRequest("Food", 100, null, null, null);
            assertEquals(4, validator.validate(missing).size());
            var complete = new MealDishRequest("Food", 100, null, null, null, BigDecimal.ONE,
                DishUnit.SERVING, null, false, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN, "Label", false);
            assertTrue(validator.validate(complete).isEmpty());
        }
    }
}
