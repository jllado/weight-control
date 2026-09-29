package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealDish;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineAutomaticEvidence;
import com.jllado.weightcontrol.domain.RoutineAutomaticTrigger;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.RoutineReminder;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.repository.FastingPeriodRepository;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.RoutineAutomaticEvidenceRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutineAutomationServiceTest {
    @Mock private RoutineRepository routineRepository;
    @Mock private RoutineAutomaticEvidenceRepository evidenceRepository;
    @Mock private RoutineCheckinRepository checkinRepository;
    @Mock private MealRepository mealRepository;
    @Mock private WorkoutRepository workoutRepository;
    @Mock private FastingPeriodRepository fastingPeriodRepository;
    @InjectMocks private RoutineAutomationService service;

    @Test
    void matchesEachConfiguredTriggerAndOnlyCompletedFastOverTwelveHours() {
        User user = user();
        List<Routine> routines = java.util.Arrays.stream(RoutineAutomaticTrigger.values())
            .filter(trigger -> trigger != RoutineAutomaticTrigger.NONE)
            .map(trigger -> routine(user, trigger))
            .toList();
        RoutineReminder snoozedReminder = new RoutineReminder();
        snoozedReminder.setRoutine(routines.stream().filter(routine -> routine.getAutomaticTrigger() == RoutineAutomaticTrigger.FRUIT_MEAL).findFirst().orElseThrow());
        snoozedReminder.setReminderTime(java.time.LocalTime.of(8, 0));
        snoozedReminder.setReminderSnoozedUntil(OffsetDateTime.parse("2026-09-28T08:15:00+02:00"));
        snoozedReminder.getRoutine().getReminders().add(snoozedReminder);
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(routines);
        when(evidenceRepository.findByRoutine(any())).thenReturn(List.of());
        when(checkinRepository.findByRoutineOrderByCheckedAtAsc(any())).thenReturn(List.of());

        Meal fruitMeal = new Meal();
        fruitMeal.setId(10L);
        fruitMeal.setMealDate(LocalDate.of(2026, 9, 28));
        MealDish fruit = new MealDish();
        fruit.setFruit(true);
        fruitMeal.setDishes(List.of(fruit));
        Meal secondFruitMeal = new Meal();
        secondFruitMeal.setId(11L);
        secondFruitMeal.setMealDate(fruitMeal.getMealDate());
        secondFruitMeal.setDishes(List.of(fruit));
        when(mealRepository.findByUserOrderByMealDateDescIdAsc(user)).thenReturn(List.of(fruitMeal, secondFruitMeal));

        Workout workout = new Workout();
        workout.setId(20L);
        workout.setWorkoutDate(LocalDate.of(2026, 9, 28));
        workout.setLines(List.of(
            line(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, null),
            line(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, null),
            line(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, null),
            line(ExerciseType.WARM_UP, ExerciseTrackingMode.REPS, "mcgill-big-three")
        ));
        when(workoutRepository.findByUserOrderByWorkoutDateDesc(user)).thenReturn(List.of(workout));

        FastingPeriod completed = fast(OffsetDateTime.parse("2026-09-27T07:00:00Z"), OffsetDateTime.parse("2026-09-27T19:01:00Z"));
        FastingPeriod exactlyTwelve = fast(OffsetDateTime.parse("2026-09-26T07:00:00Z"), OffsetDateTime.parse("2026-09-26T19:00:00Z"));
        FastingPeriod ongoing = fast(OffsetDateTime.parse("2026-09-25T07:00:00Z"), null);
        when(fastingPeriodRepository.findByUserOrderByStartTimeDescIdDesc(user)).thenReturn(List.of(completed, exactlyTwelve, ongoing));

        service.reconcile(user);

        ArgumentCaptor<RoutineAutomaticEvidence> evidence = ArgumentCaptor.forClass(RoutineAutomaticEvidence.class);
        verify(evidenceRepository, org.mockito.Mockito.times(7)).save(evidence.capture());
        assertEquals(7, evidence.getAllValues().size());
        assertEquals(3, evidence.getAllValues().stream().map(RoutineAutomaticEvidence::getSourceKind).distinct().count());
        ArgumentCaptor<RoutineCheckin> checkins = ArgumentCaptor.forClass(RoutineCheckin.class);
        verify(checkinRepository, org.mockito.Mockito.times(6)).save(checkins.capture());
        assertEquals(6, checkins.getAllValues().stream().filter(checkin -> !checkin.isManualCompletion()).count());
        assertEquals(0, checkins.getAllValues().stream().filter(checkin -> checkin.getCheckedAt().equals(com.jllado.weightcontrol.util.DateTimes.startOfDay(LocalDate.of(2026, 9, 26)))).count());
        assertNull(snoozedReminder.getReminderSnoozedUntil());
    }

    @Test
    void preservesManualCompletionWhenEvidenceIsReconciled() {
        User user = user();
        Routine routine = routine(user, RoutineAutomaticTrigger.FRUIT_MEAL);
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of(routine));
        Meal fruitMeal = new Meal();
        fruitMeal.setId(10L);
        fruitMeal.setMealDate(LocalDate.of(2026, 9, 28));
        MealDish fruit = new MealDish();
        fruit.setFruit(true);
        fruitMeal.setDishes(List.of(fruit));
        when(mealRepository.findByUserOrderByMealDateDescIdAsc(user)).thenReturn(List.of(fruitMeal));
        when(workoutRepository.findByUserOrderByWorkoutDateDesc(user)).thenReturn(List.of());
        when(fastingPeriodRepository.findByUserOrderByStartTimeDescIdDesc(user)).thenReturn(List.of());
        when(evidenceRepository.findByRoutine(routine)).thenReturn(List.of());
        RoutineCheckin manual = new RoutineCheckin();
        manual.setRoutine(routine);
        manual.setCheckedAt(OffsetDateTime.parse("2026-09-28T00:00:00+02:00"));
        manual.setManualCompletion(true);
        when(checkinRepository.findByRoutineOrderByCheckedAtAsc(routine)).thenReturn(List.of(manual));

        service.reconcile(user);

        verify(checkinRepository, never()).save(any());
        verify(checkinRepository, never()).delete(any(RoutineCheckin.class));
    }

    @Test
    void removesAutomaticCompletionWhenItsLastQualifyingSourceDisappears() {
        User user = user();
        Routine routine = routine(user, RoutineAutomaticTrigger.FRUIT_MEAL);
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of(routine));
        when(mealRepository.findByUserOrderByMealDateDescIdAsc(user)).thenReturn(List.of());
        when(workoutRepository.findByUserOrderByWorkoutDateDesc(user)).thenReturn(List.of());
        when(fastingPeriodRepository.findByUserOrderByStartTimeDescIdDesc(user)).thenReturn(List.of());
        RoutineAutomaticEvidence evidence = new RoutineAutomaticEvidence();
        evidence.setRoutine(routine);
        evidence.setSourceKind("MEAL");
        evidence.setSourceKey("10");
        evidence.setEventDate(LocalDate.of(2026, 9, 28));
        when(evidenceRepository.findByRoutine(routine)).thenReturn(List.of(evidence));
        RoutineCheckin automatic = new RoutineCheckin();
        automatic.setRoutine(routine);
        automatic.setCheckedAt(com.jllado.weightcontrol.util.DateTimes.startOfDay(LocalDate.of(2026, 9, 28)));
        automatic.setManualCompletion(false);
        when(checkinRepository.findByRoutineOrderByCheckedAtAsc(routine)).thenReturn(List.of(automatic));

        service.reconcile(user);

        verify(evidenceRepository).delete(evidence);
        verify(checkinRepository).delete(automatic);
        verify(routineRepository).save(routine);
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    private static Routine routine(User user, RoutineAutomaticTrigger trigger) {
        Routine routine = new Routine();
        routine.setId((long) trigger.ordinal() + 1);
        routine.setUser(user);
        routine.setStartDate(OffsetDateTime.parse("2026-01-01T00:00:00+01:00"));
        routine.setAutomaticTrigger(trigger);
        routine.setCurrentStrike(0);
        routine.setBestStrike(0);
        return routine;
    }

    private static WorkoutLine line(ExerciseType type, ExerciseTrackingMode mode, String imageKey) {
        Exercise exercise = new Exercise();
        exercise.setExerciseType(type);
        exercise.setTrackingMode(mode);
        exercise.setBuiltInImageKey(imageKey);
        WorkoutLine line = new WorkoutLine();
        line.setExercise(exercise);
        return line;
    }

    private static FastingPeriod fast(OffsetDateTime start, OffsetDateTime end) {
        FastingPeriod period = new FastingPeriod();
        period.setStartTime(start);
        period.setEndTime(end);
        return period;
    }
}
