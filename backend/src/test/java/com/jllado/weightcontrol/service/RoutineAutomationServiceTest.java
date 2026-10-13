package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.repository.FastingPeriodRepository;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.RoutineAutomaticEvidenceRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Mock private SleepRepository sleepRepository;
    @InjectMocks private RoutineAutomationService service;

    @ParameterizedTest
    @CsvSource({
        "2026-09-27T23:00:00+02:00,2026-09-28T05:59:59+02:00,28800,false",
        "2026-09-27T23:00:00+02:00,2026-09-28T06:00:00+02:00,10800,true",
        "2026-03-29T00:00:00+01:00,2026-03-29T07:00:00+02:00,28800,false",
        "2026-03-29T00:00:00+01:00,2026-03-29T08:00:00+02:00,10800,true",
        "2026-10-25T00:00:00+02:00,2026-10-25T06:00:00+01:00,10800,true"
    })
    void bedTimeUsesExactElapsedSevenHourThresholdAcrossDstInsteadOfAsleepDuration(String start, String end, int asleepSeconds, boolean qualifies) {
        User user = user();
        prepareRule(user, "SLEEP_BED_AT_LEAST_7_HOURS");
        Sleep sleep = sleep(start, end);
        sleep.setTotalSleepDuration(asleepSeconds);
        when(sleepRepository.findByUserOrderBySleepDateDesc(user)).thenReturn(List.of(sleep));

        service.reconcile(user);

        assertAutomaticDate(qualifies, sleep.getSleepDate());
    }

    @ParameterizedTest
    @CsvSource({
        "2026-08-12T04:59:59Z,true", "2026-08-12T05:00:00Z,false", "2026-08-12T06:00:00+02:00,true",
        "2026-12-12T05:59:59Z,true", "2026-12-12T06:00:00Z,false", "2026-12-12T07:00:01+01:00,false"
    })
    void wakeTimeUsesStrictMadridSevenOClockCutoffAndRecordedSleepDate(String end, boolean qualifies) {
        User user = user();
        prepareRule(user, "WAKE_BEFORE_7");
        OffsetDateTime bedtimeEnd = OffsetDateTime.parse(end);
        Sleep sleep = sleep(bedtimeEnd.minusHours(8).toString(), end);
        sleep.setSleepDate(LocalDate.of(2026, 8, 10));
        when(sleepRepository.findByUserOrderBySleepDateDesc(user)).thenReturn(List.of(sleep));

        service.reconcile(user);

        assertAutomaticDate(qualifies, LocalDate.of(2026, 8, 10));
    }

    @ParameterizedTest
    @CsvSource({"Kiwi,false,true", "'  KiWi  ',false,true", "KIWI,true,true", "Kiwis,true,false", "Kiwi smoothie,true,false", "Apple,true,false"})
    void kiwiUsesExactFoodSnapshotNameIndependentlyFromFruitClassification(String name, boolean fruit, boolean qualifies) {
        User user = user();
        prepareRule(user, "KIWI_MEAL");
        Meal meal = new Meal(); meal.setId(10L); meal.setMealDate(LocalDate.of(2026, 9, 28));
        MealDish food = new MealDish(); food.setName(name); food.setFruit(fruit); meal.setDishes(List.of(food));
        when(mealRepository.findByUserOrderByMealDateDescIdAsc(user)).thenReturn(List.of(meal));

        service.reconcile(user);

        assertAutomaticDate(qualifies, meal.getMealDate());
    }

    @ParameterizedTest
    @CsvSource(value = {"19:00:00,STRETCHING,false", "19:00:01,STRETCHING,true", "18:59:59,STRETCHING,false", "NULL,STRETCHING,false", "20:00:00,TRAINING,false", "20:00:00,WARM_UP,false"}, nullValues = "NULL")
    void nightStretchingRequiresRecordedStartStrictlyAfterNineteenAndStretchingType(String time, ExerciseType type, boolean qualifies) {
        User user = user();
        prepareRule(user, "NIGHT_STRETCHING_WORKOUT");
        Workout workout = new Workout(); workout.setId(20L); workout.setWorkoutDate(LocalDate.of(2026, 9, 28));
        workout.setStartTime(time == null ? null : java.time.LocalTime.parse(time));
        workout.setLines(List.of(line(type, ExerciseTrackingMode.SECONDS, null)));
        when(workoutRepository.findByUserOrderByWorkoutDateDesc(user)).thenReturn(List.of(workout));

        service.reconcile(user);

        assertAutomaticDate(qualifies, workout.getWorkoutDate());
    }

    @ParameterizedTest
    @CsvSource({"Sitting-Rising,true", "' sitting-rising ',true", "SITTING-RISING,true", "Sitting Rising,false", "Sitting-Rising test,false", "Squat,false"})
    void sittingRisingUsesExactExerciseNameWithoutRequiringBuiltInIdentity(String name, boolean qualifies) {
        User user = user();
        prepareRule(user, "SITTING_RISING_WORKOUT");
        Workout workout = new Workout(); workout.setId(20L); workout.setWorkoutDate(LocalDate.of(2026, 9, 28));
        WorkoutLine line = line(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, null);
        line.getExercise().setName(name); workout.setLines(List.of(line));
        when(workoutRepository.findByUserOrderByWorkoutDateDesc(user)).thenReturn(List.of(workout));

        service.reconcile(user);

        assertAutomaticDate(qualifies, workout.getWorkoutDate());
    }

    @ParameterizedTest
    @CsvSource({"SLEEP_BED_AT_LEAST_7_HOURS,false,true", "SLEEP_BED_AT_LEAST_7_HOURS,true,false", "SLEEP_BED_AT_LEAST_7_HOURS,false,false", "WAKE_BEFORE_7,false,true", "WAKE_BEFORE_7,true,false", "WAKE_BEFORE_7,false,false"})
    void historicalSleepWithoutCompleteBedtimeEndpointsDoesNotQualify(String rule, boolean hasStart, boolean hasEnd) {
        User user = user();
        prepareRule(user, rule);
        Sleep sleep = sleep("2026-09-27T22:00:00+02:00", "2026-09-28T06:00:00+02:00");
        if (!hasStart) sleep.setBedtimeStart(null);
        if (!hasEnd) sleep.setBedtimeEnd(null);
        when(sleepRepository.findByUserOrderBySleepDateDesc(user)).thenReturn(List.of(sleep));

        service.reconcile(user);

        assertAutomaticDate(false, sleep.getSleepDate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SLEEP_BED_AT_LEAST_7_HOURS", "KIWI_MEAL", "WAKE_BEFORE_7", "NIGHT_STRETCHING_WORKOUT", "SITTING_RISING_WORKOUT"})
    void newRulesExcludeSourceDatesBeforeTheOriginalRoutineStart(String rule) {
        User user = user(); prepareRule(user, rule);
        LocalDate date = LocalDate.of(2025, 12, 31);
        Meal meal = new Meal(); meal.setId(10L); meal.setMealDate(date);
        MealDish food = new MealDish(); food.setName("Kiwi"); meal.setDishes(List.of(food));
        when(mealRepository.findByUserOrderByMealDateDescIdAsc(user)).thenReturn(List.of(meal));
        Workout workout = new Workout(); workout.setId(20L); workout.setWorkoutDate(date); workout.setStartTime(java.time.LocalTime.of(20, 0));
        WorkoutLine sitting = line(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, null); sitting.getExercise().setName("Sitting-Rising");
        workout.setLines(List.of(sitting, line(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, null)));
        when(workoutRepository.findByUserOrderByWorkoutDateDesc(user)).thenReturn(List.of(workout));
        Sleep sleep = sleep("2025-12-30T22:00:00+01:00", "2025-12-31T06:00:00+01:00");
        when(sleepRepository.findByUserOrderBySleepDateDesc(user)).thenReturn(List.of(sleep));

        service.reconcile(user);

        assertAutomaticDate(false, date);
    }

    private void prepareRule(User user, String name) {
        assertTrue(java.util.Arrays.stream(RoutineAutomaticTrigger.values()).anyMatch(rule -> rule.name().equals(name)), "Missing fixed automatic rule " + name);
        Routine routine = routine(user, RoutineAutomaticTrigger.valueOf(name));
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of(routine));
        when(evidenceRepository.findByRoutine(routine)).thenReturn(List.of());
        when(checkinRepository.findByRoutineOrderByCheckedAtAsc(routine)).thenReturn(List.of());
    }

    private void assertAutomaticDate(boolean expected, LocalDate date) {
        ArgumentCaptor<RoutineCheckin> checkin = ArgumentCaptor.forClass(RoutineCheckin.class);
        verify(checkinRepository, org.mockito.Mockito.times(expected ? 1 : 0)).save(checkin.capture());
        verify(evidenceRepository, org.mockito.Mockito.times(expected ? 1 : 0)).save(any());
        if (expected) {
            assertEquals(com.jllado.weightcontrol.util.DateTimes.startOfDay(date), checkin.getValue().getCheckedAt());
            assertFalse(checkin.getValue().isManualCompletion());
        }
    }

    private Sleep sleep(String start, String end) {
        Sleep sleep = new Sleep(); sleep.setId(30L);
        sleep.setBedtimeStart(OffsetDateTime.parse(start)); sleep.setBedtimeEnd(OffsetDateTime.parse(end));
        sleep.setSleepDate(com.jllado.weightcontrol.util.DateTimes.toLocalDate(sleep.getBedtimeEnd()));
        return sleep;
    }

    @Test
    void matchesOriginalFruitWorkoutAndCompletedFastingRules() {
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
        fruit.setName("Apple");
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
        fruit.setName("Apple");
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
        routine.setAutomaticCompletionEnabled(true);
        routine.setCurrentStrike(0);
        routine.setBestStrike(0);
        return routine;
    }

    private static WorkoutLine line(ExerciseType type, ExerciseTrackingMode mode, String imageKey) {
        Exercise exercise = new Exercise();
        exercise.setExerciseType(type);
        exercise.setName("Exercise");
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
