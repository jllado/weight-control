package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseRequest;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.repository.ExerciseRepository;
import com.jllado.weightcontrol.repository.WorkoutLineRepository;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {

    @Mock
    private ExerciseRepository repository;

    @Mock
    private WorkoutLineRepository workoutLineRepository;

    @Mock
    private ExerciseImageStorage imageStorage;

    @Mock
    private com.jllado.weightcontrol.repository.StretchingSetEntryRepository stretchingEntries;

    @InjectMocks
    private ExerciseService service;


    @Test
    void stretchingCatalogSupportsCreateAndEditWithSecondsOnly() {
        when(repository.save(any(Exercise.class))).thenAnswer(call -> call.getArgument(0));
        Exercise exercise = service.create(new ExerciseRequest(" Calf stretch ", " Hold comfortably. ", ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING));
        assertEquals("Calf stretch", exercise.getName());
        assertEquals(ExerciseType.STRETCHING, exercise.getExerciseType());
        assertEquals(ExerciseTrackingMode.SECONDS, exercise.getTrackingMode());
        exercise.setId(1L);
        when(repository.findForUpdateById(1L)).thenReturn(Optional.of(exercise));
        when(workoutLineRepository.existsByExercise(exercise)).thenReturn(true);
        assertEquals("Updated", service.update(1L, new ExerciseRequest("Updated", "Updated description", ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING)).getName());
        assertThrows(BadRequestException.class, () -> service.update(1L, new ExerciseRequest("Updated", "desc", ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING)));
        assertThrows(BadRequestException.class, () -> service.delete(1L));
        when(workoutLineRepository.existsByExercise(exercise)).thenReturn(false);
        service.delete(1L);
        verify(repository).delete(exercise);
    }

    @Test
    void stretchingRejectsOtherModes() {
        for (ExerciseTrackingMode mode : List.of(ExerciseTrackingMode.REPS, ExerciseTrackingMode.CARDIO)) {
            assertThrows(BadRequestException.class, () -> service.create(new ExerciseRequest("Stretch", "desc", mode, ExerciseType.STRETCHING)));
        }
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRejectsUsedExercise() {
        Exercise exercise = new Exercise();
        exercise.setId(1L);
        when(repository.findForUpdateById(1L)).thenReturn(Optional.of(exercise));
        when(workoutLineRepository.existsByExercise(exercise)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.delete(1L));
    }

    @Test
    void updateRejectsTrackingModeChangeAfterUse() {
        Exercise exercise = new Exercise();
        exercise.setId(1L);
        exercise.setName("Push-up");
        exercise.setDescription("desc");
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        when(repository.findForUpdateById(1L)).thenReturn(Optional.of(exercise));
        when(repository.existsByNameIgnoreCaseAndIdNot("Push-up", 1L)).thenReturn(false);
        when(workoutLineRepository.existsByExercise(exercise)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.update(1L, new ExerciseRequest("Push-up", "desc", ExerciseTrackingMode.SECONDS)));
    }
}
