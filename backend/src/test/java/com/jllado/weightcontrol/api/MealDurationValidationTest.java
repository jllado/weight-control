package com.jllado.weightcontrol.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.domain.MealType;
import jakarta.validation.Validation;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class MealDurationValidationTest {
    @Test
    void requiresPositiveDurationOnlyForTimedMeals() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request(null, null)).isEmpty());
            assertTrue(validator.validate(request(LocalTime.NOON, 30)).isEmpty());
            assertFalse(validator.validate(request(LocalTime.NOON, null)).isEmpty());
            assertFalse(validator.validate(request(LocalTime.NOON, 0)).isEmpty());
            assertFalse(validator.validate(request(null, -1)).isEmpty());
        }
    }

    @Test
    void durationRoundTripsAndRejectsFractionalJson() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var request = request(LocalTime.NOON, 30);
        assertEquals(request, mapper.readValue(mapper.writeValueAsString(request), MealRequest.class));
        assertThrows(JsonProcessingException.class, () -> mapper.readValue("{\"durationMinutes\":30.5}", MealRequest.class));
    }

    private MealRequest request(LocalTime time, Integer duration) {
        return new MealRequest(LocalDate.of(2026, 8, 12), MealType.LUNCH, 500, null, null, null, time, null, duration);
    }
}
