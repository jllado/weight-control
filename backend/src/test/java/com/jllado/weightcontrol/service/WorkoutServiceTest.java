package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutLineRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutSegmentRequest;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkoutServiceTest {

    @Mock
    private WorkoutRepository repository;

    @Mock
    private ExerciseService exerciseService;

    @InjectMocks
    private WorkoutService service;

    @Test
    void createAcceptsCardioIntervals() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(3L);
        exercise.setTrackingMode(ExerciseTrackingMode.CARDIO);
        when(repository.findByUserAndWorkoutDate(user, LocalDate.now(DateTimes.USER_ZONE))).thenReturn(Optional.empty());
        when(exerciseService.require(3L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            "cardio",
            List.of(new WorkoutLineRequest(3L, 42, List.of(
                new WorkoutSegmentRequest(null, 300, null, BigDecimal.valueOf(8.5), BigDecimal.ONE, 5, null)
            )))
        );

        assertDoesNotThrow(() -> service.create(user, request));
    }

    @Test
    void createRejectsDurationOutsideFiveSecondSteps() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(2L);
        exercise.setTrackingMode(ExerciseTrackingMode.SECONDS);
        when(repository.findByUserAndWorkoutDate(user, LocalDate.now(DateTimes.USER_ZONE))).thenReturn(Optional.empty());
        when(exerciseService.require(2L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(2L, null, List.of(
                new WorkoutSegmentRequest(null, 17, null, null, null, null, null)
            )))
        );

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsRepFieldInsideCardio() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(4L);
        exercise.setTrackingMode(ExerciseTrackingMode.CARDIO);
        when(repository.findByUserAndWorkoutDate(user, LocalDate.now(DateTimes.USER_ZONE))).thenReturn(Optional.empty());
        when(exerciseService.require(4L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(4L, null, List.of(
                new WorkoutSegmentRequest(12, 300, null, null, null, null, null)
            )))
        );

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsDuplicateWorkoutDate() {
        User user = new User();
        user.setId(1L);
        Workout existing = new Workout();
        existing.setId(9L);
        when(repository.findByUserAndWorkoutDate(user, LocalDate.now(DateTimes.USER_ZONE))).thenReturn(Optional.of(existing));

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(1L, null, List.of(
                new WorkoutSegmentRequest(10, null, null, null, null, null, null)
            )))
        );

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }
}
