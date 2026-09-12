package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutLineRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutSegmentRequest;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutAssessment;
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
    void stretchingOnlyWorkoutRoundTripsTimedSetsAndValidatesDurationAndWeight() {
        User user = new User();
        Exercise exercise = new Exercise();
        exercise.setId(3L);
        exercise.setName("Calf stretch");
        exercise.setExerciseType(ExerciseType.STRETCHING);
        exercise.setTrackingMode(ExerciseTrackingMode.SECONDS);
        when(exerciseService.require(3L)).thenReturn(exercise);
        when(repository.save(any(Workout.class))).thenAnswer(call -> call.getArgument(0));
        var date = LocalDate.now(DateTimes.USER_ZONE);
        var hold = new WorkoutSegmentRequest(null, 30, null, null, null, null, null, null, null);
        Workout workout = service.create(user, new WorkoutRequest(date, null, List.of(new WorkoutLineRequest(3L, null, null, List.of(hold, hold), null)), null, null, null, null, null, null));
        var response = com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutResponse.from(workout);
        assertEquals(ExerciseType.STRETCHING, response.lines().getFirst().exerciseType());
        assertEquals(List.of(30, 30), response.lines().getFirst().sets().stream().map(set -> set.durationSeconds()).toList());
        assertNull(response.lines().getFirst().sets().getFirst().weight());
        for (Integer duration : new Integer[]{null, 0, -5, 32}) {
            var invalid = new WorkoutSegmentRequest(null, duration, null, null, null, null, null, null, null);
            assertThrows(BadRequestException.class, () -> service.create(user, new WorkoutRequest(date, null, List.of(new WorkoutLineRequest(3L, null, null, List.of(invalid), null)), null, null, null, null, null, null)));
        }
        var weighted = new WorkoutSegmentRequest(null, 30, BigDecimal.ONE, null, null, null, null, null, null);
        assertThrows(BadRequestException.class, () -> service.create(user, new WorkoutRequest(date, null, List.of(new WorkoutLineRequest(3L, null, null, List.of(weighted), null)), null, null, null, null, null, null)));
    }

    @Test
    void createAcceptsCardioIntervals() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(3L);
        exercise.setTrackingMode(ExerciseTrackingMode.CARDIO);
        when(exerciseService.require(3L)).thenReturn(exercise);
        when(repository.save(any(Workout.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            "cardio",
            List.of(new WorkoutLineRequest(3L, 42, 143, List.of(
                new WorkoutSegmentRequest(null, 300, null, BigDecimal.valueOf(8.5), BigDecimal.valueOf(1.25), BigDecimal.ONE, 5, null, null)
            ), null)),
            null, null, null, null, null
        , null);

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
        when(repository.findPreloadSessions(user, date, PageRequest.of(0, 40))).thenReturn(List.of(preload));

        var result = service.findDashboardWorkouts(user, date);

        assertEquals(List.of(current), result.currentWorkouts());
        assertEquals(List.of(previous), result.previousWeekWorkouts());
        assertEquals(List.of(preload), result.preloadWorkouts());
        verify(repository).findByUserAndWorkoutDateIn(user, List.of(date, date.minusWeeks(1)));
        verify(repository).findPreloadSessions(user, date, PageRequest.of(0, 40));
    }

    @Test
    void diaryLoadsOnlyTheRequestedPageAndPreloadsAreBounded() {
        User user = new User();
        LocalDate date = LocalDate.of(2026, 8, 20);
        Workout workout = new Workout();
        workout.setWorkoutDate(date);
        when(repository.findByUserOrderByWorkoutDateDesc(user, PageRequest.of(2, 10))).thenReturn(new PageImpl<>(List.of(workout), PageRequest.of(2, 10), 31));
        when(repository.findPreloadSessions(user, date, PageRequest.of(0, 40))).thenReturn(List.of(workout));

        var page = service.findDiaryPage(user, 2, 10);
        var preloads = service.findPreloadWorkouts(user, date);

        assertEquals(31, page.getTotalElements());
        assertEquals(List.of(workout), preloads);
        verify(repository).findByUserOrderByWorkoutDateDesc(user, PageRequest.of(2, 10));
        verify(repository).findPreloadSessions(user, date, PageRequest.of(0, 40));
    }

    @Test
    void updateDeletesTheSavedAssessment() {
        User user = new User();
        user.setId(1L);
        Workout workout = new Workout();
        workout.setId(9L);
        workout.setUser(user);
        workout.setWorkoutDate(LocalDate.now(DateTimes.USER_ZONE));
        WorkoutAssessment assessment = new WorkoutAssessment();
        assessment.setWorkout(workout);
        workout.setAssessment(assessment);
        Exercise exercise = new Exercise();
        exercise.setId(1L);
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        WorkoutRequest request = new WorkoutRequest(
            workout.getWorkoutDate(),
            "Updated workout",
            List.of(new WorkoutLineRequest(1L, null, null, List.of(
                new WorkoutSegmentRequest(8, null, null, null, null, null, null, null, null)
            ), null)),
            null, null, null, null, null
        , null);
        when(repository.findWithLinesById(9L)).thenReturn(Optional.of(workout));
        when(exerciseService.require(1L)).thenReturn(exercise);
        when(repository.save(workout)).thenReturn(workout);

        service.update(user, 9L, request);

        assertNull(workout.getAssessment());
    }

    @Test
    void createRejectsDurationOutsideFiveSecondSteps() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(2L);
        exercise.setTrackingMode(ExerciseTrackingMode.SECONDS);
        when(exerciseService.require(2L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(2L, null, null, List.of(
                new WorkoutSegmentRequest(null, 17, null, null, null, null, null, null, null)
            ), null)),
            null, null, null, null, null
        , null);

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsRepFieldInsideCardio() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(4L);
        exercise.setTrackingMode(ExerciseTrackingMode.CARDIO);
        when(exerciseService.require(4L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(4L, null, null, List.of(
                new WorkoutSegmentRequest(12, 300, null, null, null, null, null, null, null)
            ), null)),
            null, null, null, null, null
        , null);

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createAllowsRepeatedWorkoutDatesWithDistinctSessionReferences() {
        User user = new User();
        Exercise exercise = new Exercise(); exercise.setId(1L); exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        when(exerciseService.require(1L)).thenReturn(exercise);
        when(repository.save(any(Workout.class))).thenAnswer(call -> call.getArgument(0));
        var request = new WorkoutRequest(LocalDate.now(DateTimes.USER_ZONE), null,
            List.of(new WorkoutLineRequest(1L, null, null, List.of(new WorkoutSegmentRequest(10, null, null, null, null, null, null, null, null)), null)), null, null, null, null, null, null);
        var first = service.create(user, request);
        var second = service.create(user, request);
        assertEquals(first.getWorkoutDate(), second.getWorkoutDate());
        org.junit.jupiter.api.Assertions.assertNotEquals(first.getSessionReference(), second.getSessionReference());
    }

    @Test
    void createRejectsAverageHeartRateInsideNonCardio() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(5L);
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        when(exerciseService.require(5L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(5L, null, 140, List.of(
                new WorkoutSegmentRequest(10, null, null, null, null, null, null, null, null)
            ), null)),
            null, null, null, null, null
        , null);

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsDistanceInsideNonCardio() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(6L);
        exercise.setTrackingMode(ExerciseTrackingMode.SECONDS);
        when(exerciseService.require(6L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(6L, null, null, List.of(
                new WorkoutSegmentRequest(null, 300, null, null, BigDecimal.ONE, null, null, null, null)
            ), null)),
            null, null, null, null, null
        , null);

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsNegativeAverageHeartRate() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(7L);
        exercise.setTrackingMode(ExerciseTrackingMode.CARDIO);
        when(exerciseService.require(7L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(7L, null, -1, List.of(
                new WorkoutSegmentRequest(null, 300, null, null, null, null, null, null, null)
            ), null)),
            null, null, null, null, null
        , null);

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }

    @Test
    void createRejectsNegativeDistance() {
        User user = new User();
        user.setId(1L);
        Exercise exercise = new Exercise();
        exercise.setId(8L);
        exercise.setTrackingMode(ExerciseTrackingMode.CARDIO);
        when(exerciseService.require(8L)).thenReturn(exercise);

        WorkoutRequest request = new WorkoutRequest(
            LocalDate.now(DateTimes.USER_ZONE),
            null,
            List.of(new WorkoutLineRequest(8L, null, null, List.of(
                new WorkoutSegmentRequest(null, 300, null, null, BigDecimal.valueOf(-1), null, null, null, null)
            ), null)),
            null, null, null, null, null
        , null);

        assertThrows(BadRequestException.class, () -> service.create(user, request));
    }
}
