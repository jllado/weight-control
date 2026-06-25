package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseRequest;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.repository.ExerciseRepository;
import com.jllado.weightcontrol.repository.WorkoutLineRepository;
import java.util.Optional;
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

    @InjectMocks
    private ExerciseService service;

    @Test
    void deleteRejectsUsedExercise() {
        Exercise exercise = new Exercise();
        exercise.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(exercise));
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
        when(repository.findById(1L)).thenReturn(Optional.of(exercise));
        when(repository.existsByNameIgnoreCaseAndIdNot("Push-up", 1L)).thenReturn(false);
        when(workoutLineRepository.existsByExercise(exercise)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.update(1L, new ExerciseRequest("Push-up", "desc", ExerciseTrackingMode.SECONDS)));
    }
}
