package com.jllado.weightcontrol.api.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.api.dto.ReflectionDtos.SaveReflectionRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReflectionDtosTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCompactReflection() {
        SaveReflectionRequest request = request(
            "Steady progress",
            "This week shows a small but consistent improvement.",
            List.of("Mood improved this week."),
            List.of("Sleep remains inconsistent."),
            List.of("Keep a consistent bedtime.")
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsAnythingOtherThanOneInsightPerSection() {
        SaveReflectionRequest request = request(
            "Steady progress",
            "This week shows a small but consistent improvement.",
            List.of(),
            List.of("Sleep remains inconsistent.", "Calories varied widely."),
            List.of("Keep a consistent bedtime.")
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsTextThatExceedsCompactLimits() {
        SaveReflectionRequest request = request(
            "T".repeat(81),
            "S".repeat(201),
            List.of("P".repeat(121)),
            List.of("W".repeat(121)),
            List.of("A".repeat(121))
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    private SaveReflectionRequest request(
        String title,
        String summary,
        List<String> positiveSignals,
        List<String> watchouts,
        List<String> nextActions
    ) {
        return new SaveReflectionRequest(title, summary, positiveSignals, watchouts, nextActions);
    }
}
