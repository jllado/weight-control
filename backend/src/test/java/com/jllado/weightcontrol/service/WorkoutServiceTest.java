package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
        when(repository.save(any(Workout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            "cardio",
            List.of(new WorkoutLineRequest(3L, 42, 143, List.of(
                new WorkoutSegmentRequest(null, 300, null, BigDecimal.valueOf(8.5), BigDecimal.valueOf(1.25), BigDecimal.ONE, 5, null)
            )))
        );

        Workout workout = assertDoesNotThrow(() -> service.create(user, request));

        assertEquals(143, workout.getLines().getFirst().getAverageHeartRate());
        assertEquals(new BigDecimal("1.25"), workout.getLines().getFirst().getSegments().getFirst().getDistanceKm());
    }

    @Test
    void dashboardWorkoutsLoadOnlyTheSelectedDatesAndPreloadCandidates() {
        User user = new User();
        LocalDate date = LocalDate.of(2026, 8, 20);
        Workout current = new Workout();
        current.setWorkoutDate(date);
        Workout previous = new Workout();
        previous.setWorkoutDate(date.minusWeeks(1));
        Workout preload = new Workout();
        preload.setWorkoutDate(date.minusDays(2));
        when(repository.findByUserAndWorkoutDateIn(user, List.of(date, date.minusWeeks(1)))).thenReturn(List.of(previous, current));
        when(repository.findTop10ByUserAndWorkoutDateBeforeOrderByWorkoutDateDesc(user, date)).thenReturn(List.of(preload));

        var result = service.findDashboardWorkouts(user, date);

        assertEquals(current, result.currentWorkout());
        assertEquals(previous, result.previousWeekWorkout());
        assertEquals(List.of(preload), result.preloadWorkouts());
        verify(repository).findByUserAndWorkoutDateIn(user, List.of(date, date.minusWeeks(1)));
        verify(repository).findTop10ByUserAndWorkoutDateBeforeOrderByWorkoutDateDesc(user, date);
    }

    @Test
    void diaryLoadsOnlyTheRequestedPageAndPreloadsAreBounded() {
        User user = new User();
        LocalDate date = LocalDate.of(2026, 8, 20);
        Workout workout = new Workout();
        workout.setWorkoutDate(date);
        when(repository.findByUserOrderByWorkoutDateDesc(user, PageRequest.of(2, 10))).thenReturn(new PageImpl<>(List.of(workout), PageRequest.of(2, 10), 31));
        when(repository.findTop10ByUserAndWorkoutDateBeforeOrderByWorkoutDateDesc(user, date)).thenReturn(List.of(workout));

        var page = service.findDiaryPage(user, 2, 10);
        var preloads = service.findPreloadWorkouts(user, date);

        assertEquals(31, page.getTotalElements());
        assertEquals(List.of(workout), preloads);
        verify(repository).findByUserOrderByWorkoutDateDesc(user, PageRequest.of(2, 10));
        verify(repository).findTop10ByUserAndWorkoutDateBeforeOrderByWorkoutDateDesc(user, date);
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
            List.of(new WorkoutLineRequest(2L, null, null, List.of(
                new WorkoutSegmentRequest(null, 17, null, null, null, null, null, null)
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
            List.of(new WorkoutLineRequest(4L, null, null, List.of(
                new WorkoutSegmentRequest(12, 300, null, null, null, null, null, null)
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
            List.of(new WorkoutLineRequest(1L, null, null, List.of(
                new WorkoutSegmentRequest(10, null, null, null, null, null, null, null)
            )))
        );

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsAverageHeartRateInsideNonCardio() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(5L);
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        when(repository.findByUserAndWorkoutDate(user, LocalDate.now(DateTimes.USER_ZONE))).thenReturn(Optional.empty());
        when(exerciseService.require(5L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(5L, null, 140, List.of(
                new WorkoutSegmentRequest(10, null, null, null, null, null, null, null)
            )))
        );

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsDistanceInsideNonCardio() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(6L);
        exercise.setTrackingMode(ExerciseTrackingMode.SECONDS);
        when(repository.findByUserAndWorkoutDate(user, LocalDate.now(DateTimes.USER_ZONE))).thenReturn(Optional.empty());
        when(exerciseService.require(6L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(6L, null, null, List.of(
                new WorkoutSegmentRequest(null, 300, null, null, BigDecimal.ONE, null, null, null)
            )))
        );

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsNegativeAverageHeartRate() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(7L);
        exercise.setTrackingMode(ExerciseTrackingMode.CARDIO);
        when(repository.findByUserAndWorkoutDate(user, LocalDate.now(DateTimes.USER_ZONE))).thenReturn(Optional.empty());
        when(exerciseService.require(7L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(7L, null, -1, List.of(
                new WorkoutSegmentRequest(null, 300, null, null, null, null, null, null)
            )))
        );

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsNegativeDistance() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(8L);
        exercise.setTrackingMode(ExerciseTrackingMode.CARDIO);
        when(repository.findByUserAndWorkoutDate(user, LocalDate.now(DateTimes.USER_ZONE))).thenReturn(Optional.empty());
        when(exerciseService.require(8L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(8L, null, null, List.of(
                new WorkoutSegmentRequest(null, 300, null, null, BigDecimal.valueOf(-1), null, null, null)
            )))
        );

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }
}
