package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.SaveWeeklyReflectionRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class WeeklyWarningReviewContractTest {
    @Test void weeklyReflectionRequiresWarningReviewEvenWhenThereAreNoWarnings() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var body = mapper.createObjectNode().put("confirmed", true).put("title", "Weekly review").put("summary", "Recorded evidence.");
        for (var domain : new String[]{"bodyComposition", "bloodPressure", "routines", "nutrition", "trainingRecovery", "goalProgress"})
            body.putObject(domain).put("summary", "Recorded evidence.").put("nextAction", "Continue tracking.");
        body.putArray("nextWeekActions").add("Continue tracking.");
        var request = mapper.treeToValue(body, SaveWeeklyReflectionRequest.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("warningReview")));
        }
    }
}
