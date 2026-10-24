package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WorkoutPlanRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WorkoutPlanExerciseSearchTest {
    private final ExerciseService exercises = mock(ExerciseService.class);
    private final WorkoutPlanService plans = new WorkoutPlanService(mock(WorkoutPlanRepository.class), mock(UserRepository.class), exercises);

    @Test void findsCaseInsensitiveNamesAndReturnsOnlyMatchingIds() {
        when(exercises.findAll()).thenReturn(List.of(exercise(1, "Push-up"), exercise(2, "Pull-up"), exercise(3, "Incline push-up")));

        var result = plans.searchExercises(" pUsH ");

        assertEquals("pUsH", result.query());
        assertEquals(List.of(1L, 3L), result.exercises().stream().map(choice -> choice.id()).toList());
    }

    @Test void rejectsQueriesThatWouldReturnMoreThanTwentyChoices() {
        when(exercises.findAll()).thenReturn(IntStream.range(0, 21).mapToObj(index -> exercise(index + 1, "Push " + index)).toList());

        assertEquals("Too many exercises match; refine the exercise query", assertThrows(BadRequestException.class, () -> plans.searchExercises("pu")).getMessage());
        when(exercises.findAll()).thenReturn(IntStream.range(0, 20).mapToObj(index -> exercise(index + 1, "Push " + index)).toList());
        assertEquals(20, plans.searchExercises("pu").exercises().size());
    }

    @Test void requiresAUsefulSearchTerm() {
        assertEquals("Exercise query must have at least two characters", assertThrows(BadRequestException.class, () -> plans.searchExercises(" p ")).getMessage());
    }

    private Exercise exercise(long id, String name) {
        var exercise = new Exercise();
        exercise.setId(id);
        exercise.setName(name);
        exercise.setDescription("Instructions");
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        exercise.setExerciseType(ExerciseType.TRAINING);
        return exercise;
    }
}
