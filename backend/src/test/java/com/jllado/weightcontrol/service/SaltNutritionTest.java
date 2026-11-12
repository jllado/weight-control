package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaltNutritionTest {
    private final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private MealDishRequest request(String value, String reference) throws Exception {
        return mapper.readValue("""
            {"name":"Soup","calories":50,"quantity":50,"unit":"GRAM",
             "vitaminDMicrograms":0,"magnesiumMilligrams":10,"nutrientSource":"Label","nutrientsEstimated":false,
             "saltGrams":%s,"saltSource":"Product label: 1.25 g/100 g","saltEstimated":true,"nutrientSchemaVersion":3,
             "reference":{"quantity":100,"calories":100,"vitaminDMicrograms":0,"magnesiumMilligrams":20,"saltGrams":%s}}
            """.formatted(value, reference), MealDishRequest.class);
    }

    @Test void scalesSaltAndRoundTripsIndependentReferenceAndEvidence() throws Exception {
        var dish = new MealDish(); dish.setPosition(0);
        DishNutrition.apply(dish, request("0.63", "1.25"));
        var response = mapper.valueToTree(MealDishResponse.from(dish));
        assertEquals(0.63, response.path("saltGrams").asDouble());
        assertEquals(1.25, response.path("reference").path("saltGrams").asDouble());
        assertEquals("Product label: 1.25 g/100 g", response.path("saltSource").asText());
        assertTrue(response.path("saltEstimated").asBoolean());
    }

    @Test void rejectsSaltReferenceLossBeforeMutatingFood() throws Exception {
        var dish = new MealDish(); dish.setCalories(123);
        var request = request("0.63", "null");
        assertThrows(BadRequestException.class, () -> DishNutrition.apply(dish, request));
        assertEquals(123, dish.getCalories());
    }

    @Test void distinguishesKnownZeroUnknownAndPartialSaltCoverage() throws Exception {
        var dish = new MealDish(); DishNutrition.apply(dish, request("0", "0"));
        var meal = new Meal(); meal.getDishes().add(dish); meal.getDishes().add(new MealDish());
        var summary = mapper.valueToTree(NutrientSummaryService.summarize(List.of(meal, new Meal())));
        assertTrue(summary.path("saltGrams").isNumber());
        assertEquals(0, summary.path("saltGrams").asDouble());
        assertEquals(1, summary.path("saltCoverage").path("foodsWithValues").asInt());
        assertEquals(1, summary.path("saltCoverage").path("estimatedFoods").asInt());
        assertEquals(2, summary.path("totalFoods").asInt());
        assertEquals(1, summary.path("mealsWithoutFoods").asInt());
        assertTrue(mapper.valueToTree(NutrientSummaryService.summarize(List.of(new Meal()))).path("saltGrams").isNull());
    }

    @Test void saltUsesStrictLimitForCompleteAndPartialEvidence() {
        var comparison = MacroTargetComparison.valueOf("STRICT_MAXIMUM");
        for (boolean complete : List.of(true, false)) {
            assertEquals("AT_OR_ABOVE_LIMIT", NutrientAssessment.compare(new BigDecimal("5"), new BigDecimal("5"), comparison, complete).code());
            assertEquals("AT_OR_ABOVE_LIMIT", NutrientAssessment.compare(new BigDecimal("5.01"), new BigDecimal("5"), comparison, complete).code());
            assertEquals(complete ? "GOAL_MET" : "UNAVAILABLE", NutrientAssessment.compare(new BigDecimal("4.99"), new BigDecimal("5"), comparison, complete).code());
            assertEquals(complete ? "GOAL_MET" : "UNAVAILABLE", NutrientAssessment.compare(BigDecimal.ZERO, new BigDecimal("5"), comparison, complete).code());
            assertEquals("UNAVAILABLE", NutrientAssessment.compare(null, new BigDecimal("5"), comparison, complete).code());
        }
        assertEquals("AT_OR_ABOVE_LIMIT", NutrientAssessment.compare(new BigDecimal("3"), new BigDecimal("3"), comparison, true).code());
        assertEquals("GOAL_MET", NutrientAssessment.compare(new BigDecimal("5"), new BigDecimal("5"), MacroTargetComparison.MAXIMUM, true).code());
    }

    @Test void defaultSaltTargetUsesWhoFiveGrams() {
        var service = new NutrientTargetService(mock(UserRepository.class), mock(WeeklyCaloriePlanService.class));
        var salt = mapper.valueToTree(service.resolve(new User(), LocalDate.of(2026, 10, 23))).path("salt");
        assertEquals(5, salt.path("value").asDouble());
        assertEquals(5, salt.path("referenceValue").asDouble());
        assertEquals("WHO", salt.path("source").asText());
    }

    @Test void oldClientsCannotDiscardKnownSaltButVersionThreeCan() throws Exception {
        var dish = new MealDish(); DishNutrition.apply(dish, request("0", "0"));
        assertThrows(BadRequestException.class, () -> FoodSnapshotCompatibility.requireAwareReplacement(2, List.of(dish)));
        assertThrows(BadRequestException.class, () -> FoodSnapshotCompatibility.requireAwareReplacement(null, List.of(dish)));
        assertDoesNotThrow(() -> FoodSnapshotCompatibility.requireAwareReplacement(3, List.of(dish)));
        assertDoesNotThrow(() -> FoodSnapshotCompatibility.requireAwareReplacement(2, List.of(new MealDish())));
    }

    @Test void saltRequiresItsOwnProvenanceAtTheSessionBoundary() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var complete = request("0.63", "1.25");
            assertTrue(factory.getValidator().validate(complete).isEmpty());
            var json = mapper.valueToTree(complete);
            ((com.fasterxml.jackson.databind.node.ObjectNode) json).putNull("saltSource");
            assertFalse(factory.getValidator().validate(mapper.treeToValue(json, MealDishRequest.class)).isEmpty());
            ((com.fasterxml.jackson.databind.node.ObjectNode) json).putNull("saltGrams").putNull("saltEstimated");
            assertFalse(factory.getValidator().validate(mapper.treeToValue(json, MealDishRequest.class)).isEmpty(),
                "Reference-only salt must not become an unsourced consumed value during scaling");
        }
    }
}
