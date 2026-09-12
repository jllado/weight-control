package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.domain.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class StretchingBreathsTest {
    @Test void validatesUnitsAndRejectsConflictingMetrics() {
        var stretch = new Exercise(); stretch.setExerciseType(ExerciseType.STRETCHING); stretch.setTrackingMode(ExerciseTrackingMode.SECONDS);
        for (Integer count : new Integer[]{null, 0, -1}) assertThrows(BadRequestException.class, () -> WorkoutTargets.validate(stretch, StretchingUnit.BREATHS, List.of(hold(count, null, null))));
        assertDoesNotThrow(() -> WorkoutTargets.validate(stretch, StretchingUnit.BREATHS, List.of(hold(1, null, null), hold(8, null, null))));
        assertThrows(BadRequestException.class, () -> WorkoutTargets.validate(stretch, StretchingUnit.BREATHS, List.of(hold(5, 30, null))));
        assertThrows(BadRequestException.class, () -> WorkoutTargets.validate(stretch, StretchingUnit.BREATHS, List.of(hold(5, null, BigDecimal.ONE))));
        assertThrows(BadRequestException.class, () -> WorkoutTargets.validate(stretch, StretchingUnit.SECONDS, List.of(hold(5, 30, null))));
        for (var type : List.of(ExerciseType.TRAINING, ExerciseType.WARM_UP)) {
            stretch.setExerciseType(type);
            assertThrows(BadRequestException.class, () -> WorkoutTargets.validate(stretch, StretchingUnit.BREATHS, List.of(hold(5, null, null))));
        }
    }
    @Test void deserializesLegacyUnitsButRejectsFractionalBreaths() throws Exception {
        var json = new ObjectMapper();
        assertEquals(StretchingUnit.SECONDS, json.readValue("{\"exerciseId\":1,\"segments\":[{\"durationSeconds\":30}]}", WorkoutLineRequest.class).stretchingUnit());
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> json.readValue("{\"breaths\":1.5}", WorkoutSegmentRequest.class));
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> json.readValue("{\"exerciseId\":1,\"stretchingUnit\":\"BREATHS\",\"breaths\":[1.5]}", StretchingSetEntryRequest.class));
        var entry = json.readValue("{\"exerciseId\":1,\"durations\":[30]}", StretchingSetEntryRequest.class);
        assertEquals(StretchingUnit.SECONDS, entry.stretchingUnit()); assertTrue(entry.isValidHolds());
        assertFalse(json.writeValueAsString(entry).contains("validHolds"));
        assertFalse(new StretchingSetEntryRequest(1L, List.of(30), StretchingUnit.BREATHS, List.of(5)).isValidHolds());
    }
    private WorkoutSegmentRequest hold(Integer breaths, Integer seconds, BigDecimal weight) { return new WorkoutSegmentRequest(null, seconds, weight, null, null, null, null, null, breaths); }
}
