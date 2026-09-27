package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class WorkoutSupersetsTest {
    @Test void acceptsAdjacentTwoAndThreeMemberGroups() {
        assertDoesNotThrow(() -> WorkoutSupersets.validate(java.util.List.of("a1", "a1", "b2", "b2", "b2"), java.util.List.of(3, 3, 2, 2, 2)));
    }

    @Test void rejectsUnequalRoundCounts() {
        assertThrows(BadRequestException.class, () -> WorkoutSupersets.validate(java.util.List.of("a1", "a1"), java.util.List.of(2, 3)));
    }

    @Test void rejectsGroupsSeparatedByAnotherExercise() {
        assertThrows(BadRequestException.class, () -> WorkoutSupersets.validate(java.util.Arrays.asList("a1", null, "a1"), java.util.List.of(2, 1, 2)));
    }

    @Test void rejectsGroupsWithOnlyOneMember() {
        assertThrows(BadRequestException.class, () -> WorkoutSupersets.validate(java.util.List.of("a1"), java.util.List.of(1)));
    }
}
