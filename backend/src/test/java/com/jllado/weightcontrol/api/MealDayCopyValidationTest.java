package com.jllado.weightcontrol.api;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jllado.weightcontrol.api.dto.MealDtos.CopyDayRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class MealDayCopyValidationTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private static final String SNAPSHOT = """
        {"date":"2026-08-12","meals":[{"mealType":"LUNCH","mealSequence":1,"calories":43,"dishes":[
          {"name":"Legacy food","calories":43,"quantity":33.333,"unit":"GRAM","fruit":true,
           "vitaminDMicrograms":null,"omega3Milligrams":0,"nutrientSource":null,"nutrientsEstimated":null,
           "epaDhaMilligrams":0,"epaDhaSource":null,"epaDhaEstimated":null,
           "reference":{"quantity":100,"calories":101,"vitaminDMicrograms":null,"omega3Milligrams":0}}
        ]}]}
        """;

    @Test void allowsIndependentLegacyConsumedAndReferenceEvidence() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = mapper.readValue(SNAPSHOT, CopyDayRequest.class);
            assertTrue(factory.getValidator().validate(request).isEmpty());
            assertNull(request.meals().getFirst().dishes().getFirst().nutrientsEstimated());
            assertEquals(43, request.meals().getFirst().dishes().getFirst().calories());
            assertEquals(101, request.meals().getFirst().dishes().getFirst().reference().calories());
        }
    }

    @Test void rejectsInvalidStructureAndValuesWithoutStrengtheningNormalMealRequests() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String field : new String[]{"calories", "proteinGrams", "carbohydrateGrams", "fatGrams", "vitaminDMicrograms", "omega3Milligrams", "magnesiumMilligrams", "epaDhaMilligrams", "alaMilligrams", "fiberGrams", "saltGrams", "quantity"}) {
                for (boolean reference : new boolean[]{false, true}) {
                    ObjectNode body = (ObjectNode) mapper.readTree(SNAPSHOT);
                    ObjectNode food = (ObjectNode) body.at("/meals/0/dishes/0");
                    (reference ? (ObjectNode) food.get("reference") : food).put(field, -1);
                    assertFalse(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty(), field);
                }
            }
            for (String pointer : new String[]{"/meals/0/dishes/0/reference", "/meals/0/dishes/0/quantity", "/meals/0/dishes/0/unit", "/meals/0/dishes/0/name", "/meals/0/dishes/0/reference/quantity", "/meals/0/dishes/0/reference/calories", "/meals/0/mealSequence", "/meals/0/mealType", "/meals/0/calories", "/meals/0/dishes", "/date", "/meals"}) {
                ObjectNode body = (ObjectNode) mapper.readTree(SNAPSHOT);
                int split = pointer.lastIndexOf('/');
                ((ObjectNode) body.at(pointer.substring(0, split))).putNull(pointer.substring(split + 1));
                assertFalse(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty(), pointer);
            }
            var empty = mapper.readValue("{\"date\":\"2026-08-12\",\"meals\":[]}", CopyDayRequest.class);
            assertFalse(validator.validate(empty).isEmpty());
            ObjectNode body = (ObjectNode) mapper.readTree(SNAPSHOT);
            ((ObjectNode) body.at("/meals/0")).put("mealSequence", 2);
            assertFalse(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty());
            ((ObjectNode) body.at("/meals/0")).put("mealType", "SNACK");
            assertTrue(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty());
            ((ObjectNode) body.at("/meals/0/dishes/0")).put("saltGrams", 0.001);
            assertFalse(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty());
        }
    }

    @Test void requiresPositiveDurationForTimedSnapshotsAndAllowsUntimedLegacySnapshots() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            ObjectNode body = (ObjectNode) mapper.readTree(SNAPSHOT);
            ObjectNode meal = (ObjectNode) body.at("/meals/0");
            assertTrue(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty());
            meal.put("mealTime", "12:00:00");
            assertFalse(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty());
            meal.putNull("durationMinutes");
            assertFalse(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty());
            for (int duration : new int[]{0, -1}) {
                meal.put("durationMinutes", duration);
                assertFalse(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty());
            }
            meal.put("durationMinutes", 30);
            assertTrue(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty());
            meal.putNull("mealTime");
            meal.putNull("durationMinutes");
            assertTrue(validator.validate(mapper.treeToValue(body, CopyDayRequest.class)).isEmpty());
        }
    }

    @Test void rejectsFractionalCaloriesSequencesAndDurations() throws Exception {
        for (String field : new String[]{"calories", "mealSequence", "durationMinutes"}) {
            ObjectNode body = (ObjectNode) mapper.readTree(SNAPSHOT);
            ((ObjectNode) body.at("/meals/0")).put(field, 1.5);
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> mapper.treeToValue(body, CopyDayRequest.class));
        }
    }
}
