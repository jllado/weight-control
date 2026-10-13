package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest;
import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
class RoutineAutomationPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection
        MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("routine_automation"); }
    }

    @Autowired RoutineService routines;
    @Autowired com.jllado.weightcontrol.repository.RoutineRepository routineRepository;
    @Autowired RoutineAutomationService automation;
    @Autowired MealService meals;
    @Autowired WorkoutService workouts;
    @Autowired ExerciseService exercises;
    @Autowired FastingPeriodService fasts;
    @Autowired SleepService sleeps;
    @Autowired UserRepository users;
    @Autowired RoutineCheckinRepository checkins;
    @Autowired JdbcTemplate jdbc;

    private static final LocalDate DAY = LocalDate.of(2026, 8, 12);

    @Test void sleepMutationsReconcileBothRulesUsingRecordedDatesAndPreserveManualCompletion() {
        var owner = user("sleep-mutations");
        var bed = routine(owner, RoutineAutomaticTrigger.SLEEP_BED_AT_LEAST_7_HOURS);
        var wake = routine(owner, RoutineAutomaticTrigger.WAKE_BEFORE_7);
        var first = sleeps.create(owner, sleep(DAY, LocalTime.of(7, 0), 6));
        assertDates(bed); assertDates(wake);
        sleeps.update(owner, first.getId(), sleep(DAY, LocalTime.of(6, 59, 59), 7));
        for (var rule : List.of(bed, wake)) {
            assertDates(rule, DAY);
            assertEvidence(rule, "SLEEP", first.getId().toString(), DAY);
            automation.reconcile(owner);
            assertEquals(1, evidenceCount(rule));
            assertSummary(owner, rule, 1, 1, DAY);
        }
        sleeps.update(owner, first.getId(), sleep(DAY.plusDays(1), LocalTime.of(7, 0), 7));
        assertDates(bed, DAY.plusDays(1)); assertDates(wake);
        assertEvidence(bed, "SLEEP", first.getId().toString(), DAY.plusDays(1));
        var second = sleeps.create(owner, sleep(DAY, LocalTime.of(6, 0), 8));
        assertDates(bed, DAY, DAY.plusDays(1)); assertDates(wake, DAY);
        sleeps.delete(owner, first.getId());
        assertDates(bed, DAY); assertDates(wake, DAY);
        routines.checkin(owner, bed.getId(), DateTimes.startOfDay(DAY).plusHours(12));
        sleeps.update(owner, second.getId(), sleep(DAY.plusDays(2), LocalTime.of(7, 0), 6));
        assertDates(bed, DAY); assertDates(wake);
        assertEquals(0, evidenceCount(bed)); assertEquals(0, evidenceCount(wake));
        assertTrue(jdbc.queryForObject("select manual_completion from routine_checkins where routine_id = ?", Boolean.class, bed.getId()));
        sleeps.delete(owner, second.getId());
        sleeps.create(user("foreign-sleep-mutations"), sleep(DAY, LocalTime.of(6, 0), 8));
        sleeps.create(owner, sleep(DAY.minusDays(6), LocalTime.of(6, 0), 8));
        automation.reconcile(owner);
        assertDates(bed, DAY); assertDates(wake);
        assertSummary(owner, bed, 1, 1, DAY);
    }

    @Test void kiwiMealMutationsUseIndependentFoodNamesAndDeduplicatePersistedDailyEvidence() {
        var owner = user("kiwi-mutations");
        var kiwi = routine(owner, RoutineAutomaticTrigger.KIWI_MEAL);
        meals.create(owner, meal(DAY, "Kiwi smoothie", true));
        assertDates(kiwi);
        var first = meals.create(owner, meal(DAY, " kiWI ", false));
        var second = meals.create(owner, meal(DAY, "KIWI", true));
        assertDates(kiwi, DAY); assertEquals(2, evidenceCount(kiwi));
        assertEvidence(kiwi, "MEAL", first.getId().toString(), DAY);
        assertEvidence(kiwi, "MEAL", second.getId().toString(), DAY);
        automation.reconcile(owner);
        assertDates(kiwi, DAY); assertEquals(2, evidenceCount(kiwi));
        meals.create(user("foreign-kiwi-mutations"), meal(DAY.plusDays(1), "Kiwi", false));
        automation.reconcile(owner);
        assertDates(kiwi, DAY);
        meals.update(owner, first.getId(), meal(DAY.plusDays(1), "Kiwi", false));
        assertDates(kiwi, DAY, DAY.plusDays(1));
        assertEvidence(kiwi, "MEAL", first.getId().toString(), DAY.plusDays(1));
        meals.update(owner, second.getId(), meal(DAY, "Kiwis", true));
        assertDates(kiwi, DAY.plusDays(1)); assertEquals(1, evidenceCount(kiwi));
        routines.checkin(owner, kiwi.getId(), DateTimes.startOfDay(DAY.plusDays(1)).plusHours(12));
        meals.delete(owner, first.getId()); meals.delete(owner, second.getId());
        assertDates(kiwi, DAY.plusDays(1)); assertEquals(0, evidenceCount(kiwi));
        assertTrue(jdbc.queryForObject("select manual_completion from routine_checkins where routine_id = ?", Boolean.class, kiwi.getId()));
    }

    @Test void additionalWorkoutRulesReconcileTimingNamesDatesAndDeletedSourcesIndependently() {
        var owner = user("additional-workout-mutations");
        var night = routine(owner, RoutineAutomaticTrigger.NIGHT_STRETCHING_WORKOUT);
        var sitting = routine(owner, RoutineAutomaticTrigger.SITTING_RISING_WORKOUT);
        var stretch = exercise("Additional evening stretch", ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS);
        var sittingExercise = sittingRisingExercise();
        var imitation = exercise("Sitting-Rising imitation", ExerciseType.TRAINING, ExerciseTrackingMode.REPS);
        var lines = List.of(line(stretch), line(sittingExercise));
        var first = workouts.create(owner, workout(DAY, lines, LocalTime.of(19, 0)));
        assertDates(night); assertDates(sitting, DAY);
        workouts.update(owner, first.getId(), workout(DAY, lines, LocalTime.of(19, 1)));
        var second = workouts.create(owner, workout(DAY, lines, LocalTime.of(20, 0)));
        for (var rule : List.of(night, sitting)) {
            assertDates(rule, DAY); assertEquals(2, evidenceCount(rule));
            assertEvidence(rule, "WORKOUT", first.getId().toString(), DAY);
            assertEvidence(rule, "WORKOUT", second.getId().toString(), DAY);
        }
        workouts.update(owner, first.getId(), workout(DAY.plusDays(1), lines, LocalTime.of(20, 0)));
        workouts.delete(owner, second.getId());
        for (var rule : List.of(night, sitting)) {
            assertDates(rule, DAY.plusDays(1));
            assertEvidence(rule, "WORKOUT", first.getId().toString(), DAY.plusDays(1));
        }
        routines.checkin(owner, sitting.getId(), DateTimes.startOfDay(DAY.plusDays(1)).plusHours(12));
        workouts.update(owner, first.getId(), workout(DAY.plusDays(1), List.of(line(imitation)), LocalTime.of(20, 0)));
        assertDates(night); assertDates(sitting, DAY.plusDays(1));
        assertEquals(0, evidenceCount(night)); assertEquals(0, evidenceCount(sitting));
        workouts.delete(owner, first.getId());
        var untimed = workouts.create(owner, workout(DAY.plusDays(2), lines));
        assertDates(night); assertDates(sitting, DAY.plusDays(1), DAY.plusDays(2));
        workouts.delete(owner, untimed.getId());
        workouts.create(user("foreign-additional-workouts"), workout(DAY, lines, LocalTime.of(20, 0)));
        automation.reconcile(owner);
        assertDates(night); assertDates(sitting, DAY.plusDays(1));
        assertTrue(jdbc.queryForObject("select manual_completion from routine_checkins where routine_id = ?", Boolean.class, sitting.getId()));
    }

    @Test void mealMutationsReconcileSourcesDatesSummariesAndPreserveManualCompletion() {
        var owner = user("fruit-mutations");
        var routine = routine(owner, RoutineAutomaticTrigger.FRUIT_MEAL);
        meals.create(owner, meal(DAY, false));
        assertDates(routine);
        var first = meals.create(owner, meal(DAY, true));
        var second = meals.create(owner, meal(DAY, true));
        assertDates(routine, DAY);
        assertEvidence(routine, "MEAL", first.getId().toString(), DAY);
        assertEvidence(routine, "MEAL", second.getId().toString(), DAY);
        assertEquals(2, evidenceCount(routine));
        assertSummary(owner, routine, 1, 1, DAY);
        assertNull(jdbc.queryForObject("select reminder_snoozed_until from routine_reminders where routine_id = ?", java.sql.Timestamp.class, routine.getId()));

        automation.reconcile(owner);
        assertDates(routine, DAY);
        assertEquals(2, evidenceCount(routine));
        meals.update(owner, first.getId(), meal(DAY.plusDays(1), true));
        assertEvidence(routine, "MEAL", first.getId().toString(), DAY.plusDays(1));
        assertDates(routine, DAY, DAY.plusDays(1));
        assertSummary(owner, routine, 2, 2, DAY.plusDays(1));
        meals.update(owner, second.getId(), meal(DAY, false));
        assertDates(routine, DAY.plusDays(1));
        assertEquals(1, evidenceCount(routine));
        assertSummary(owner, routine, 1, 1, DAY.plusDays(1));

        routines.checkin(owner, routine.getId(), DateTimes.startOfDay(DAY.plusDays(1)).plusHours(12));
        meals.delete(owner, first.getId());
        assertDates(routine, DAY.plusDays(1));
        assertEquals(0, evidenceCount(routine));
        assertTrue(jdbc.queryForObject("select manual_completion from routine_checkins where routine_id = ?", Boolean.class, routine.getId()));
        assertSummary(owner, routine, 1, 1, DAY.plusDays(1));
    }

    @Test void workoutClassificationAndMutationsUseCatalogIdentityAndPersistOneCompletionPerDay() {
        var owner = user("workout-mutations");
        var cardioRoutine = routine(owner, RoutineAutomaticTrigger.CARDIO_WORKOUT);
        var strengthRoutine = routine(owner, RoutineAutomaticTrigger.STRENGTH_WORKOUT);
        var stretchingRoutine = routine(owner, RoutineAutomaticTrigger.STRETCHING_WORKOUT);
        var mcgillRoutine = routine(owner, RoutineAutomaticTrigger.MCGILL_BIG_THREE);
        var warmup = exercise("McGill Big Three imitation", ExerciseType.WARM_UP, ExerciseTrackingMode.REPS);
        workouts.create(owner, workout(DAY.minusDays(1), List.of(line(warmup))));
        for (var routine : List.of(cardioRoutine, strengthRoutine, stretchingRoutine, mcgillRoutine)) assertDates(routine);
        var cardio = exercise("Acceptance run", ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO);
        var strength = exercise("Acceptance strength", ExerciseType.TRAINING, ExerciseTrackingMode.REPS);
        var stretching = exercise("Acceptance stretch", ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS);
        var mcgill = exercises.findAll().stream().filter(exercise -> "mcgill-big-three".equals(exercise.getBuiltInImageKey())).findFirst().orElseThrow();
        var categories = java.util.Map.of(cardioRoutine, cardio, strengthRoutine, strength, stretchingRoutine, stretching, mcgillRoutine, mcgill);
        for (var category : categories.entrySet()) {
            var isolated = workouts.create(owner, workout(DAY.minusDays(1), List.of(line(category.getValue()))));
            for (var routine : categories.keySet()) {
                if (routine == category.getKey()) {
                    assertDates(routine, DAY.minusDays(1));
                    assertEvidence(routine, "WORKOUT", isolated.getId().toString(), DAY.minusDays(1));
                } else assertDates(routine);
            }
            workouts.delete(owner, isolated.getId());
            for (var routine : categories.keySet()) assertDates(routine);
        }
        var allLines = List.of(line(cardio), line(strength), line(stretching), line(mcgill));
        var first = workouts.create(owner, workout(DAY, allLines));
        var second = workouts.create(owner, workout(DAY, allLines));
        for (var routine : List.of(cardioRoutine, strengthRoutine, stretchingRoutine, mcgillRoutine)) {
            assertDates(routine, DAY);
            assertEvidence(routine, "WORKOUT", first.getId().toString(), DAY);
            assertEvidence(routine, "WORKOUT", second.getId().toString(), DAY);
            assertEquals(2, evidenceCount(routine));
        }
        workouts.update(owner, first.getId(), workout(DAY.plusDays(1), allLines));
        for (var routine : List.of(cardioRoutine, strengthRoutine, stretchingRoutine, mcgillRoutine)) {
            assertDates(routine, DAY, DAY.plusDays(1));
            assertEvidence(routine, "WORKOUT", first.getId().toString(), DAY.plusDays(1));
            assertSummary(owner, routine, 2, 2, DAY.plusDays(1));
        }
        workouts.delete(owner, second.getId());
        routines.checkin(owner, mcgillRoutine.getId(), DateTimes.startOfDay(DAY.plusDays(1)).plusHours(10));
        workouts.update(owner, first.getId(), workout(DAY.plusDays(1), List.of(line(warmup))));
        for (var routine : List.of(cardioRoutine, strengthRoutine, stretchingRoutine)) {
            assertDates(routine);
            assertEquals(0, evidenceCount(routine));
            assertSummary(owner, routine, 0, 0, null);
        }
        assertDates(mcgillRoutine, DAY.plusDays(1));
        assertEquals(0, evidenceCount(mcgillRoutine));
        workouts.delete(owner, first.getId());
        assertDates(mcgillRoutine, DAY.plusDays(1));
        assertTrue(jdbc.queryForObject("select manual_completion from routine_checkins where routine_id = ?", Boolean.class, mcgillRoutine.getId()));
    }

    @Test void fastingRequiresCompletedStrictlyOverTwelveHoursAndUsesMadridCompletionDate() {
        var owner = user("fast-mutations");
        var routine = routine(owner, RoutineAutomaticTrigger.FAST_OVER_12_HOURS);
        var start = OffsetDateTime.parse("2026-08-11T10:00:00Z");
        var fast = fasts.create(owner, new FastingPeriodRequest(start, start.plusHours(12), null));
        assertDates(routine);
        assertEquals(0, evidenceCount(routine));
        fasts.update(owner, fast.getId(), new FastingPeriodRequest(start, start.plusHours(12).plusMinutes(1), null));
        assertDates(routine, DAY);
        assertEvidence(routine, "FAST", start.toInstant().toString(), DAY);
        assertSummary(owner, routine, 1, 1, DAY);
        automation.reconcile(owner);
        assertDates(routine, DAY);
        assertEquals(1, evidenceCount(routine));
        var movedStart = start.plusDays(1);
        fasts.update(owner, fast.getId(), new FastingPeriodRequest(movedStart, movedStart.plusHours(12).plusMinutes(1), null));
        assertDates(routine, DAY.plusDays(1));
        assertEvidence(routine, "FAST", movedStart.toInstant().toString(), DAY.plusDays(1));
        assertEquals(1, evidenceCount(routine));
        fasts.update(owner, fast.getId(), new FastingPeriodRequest(movedStart, movedStart.plusHours(12), null));
        assertDates(routine);
        assertSummary(owner, routine, 0, 0, null);
        fasts.update(owner, fast.getId(), new FastingPeriodRequest(movedStart, movedStart.plusHours(13), null));
        routines.checkin(owner, routine.getId(), DateTimes.startOfDay(DAY.plusDays(1)).plusHours(10));
        fasts.delete(owner, fast.getId());
        assertDates(routine, DAY.plusDays(1));
        assertEquals(0, evidenceCount(routine));

        // Real meal saves create an active automatic fast without a completion time.
        meals.create(owner, new MealRequest(DAY.plusDays(3), MealType.LUNCH, 100, null, null, null, LocalTime.NOON, null, List.of(), 30));
        assertTrue(fasts.findAll(owner).stream().anyMatch(period -> period.getEndTime() == null));
        assertDates(routine, DAY.plusDays(1));
        assertEquals(0, evidenceCount(routine));
    }

    @Test void timedMealMutationsRebuildCompletedAutomaticFastingEvidence() {
        var owner = user("automatic-fast-meals");
        var routine = routine(owner, RoutineAutomaticTrigger.FAST_OVER_12_HOURS);
        var dinner = meals.create(owner, timedMeal(DAY, LocalTime.of(18, 0), 30));
        assertDates(routine);
        var breakfast = meals.create(owner, timedMeal(DAY.plusDays(1), LocalTime.of(7, 0), 30));
        assertDates(routine, DAY.plusDays(1));
        var key = DateTimes.startOfDay(DAY).plusHours(18).plusMinutes(30).toInstant().toString();
        assertEvidence(routine, "FAST", key, DAY.plusDays(1));
        assertEquals(1, evidenceCount(routine));
        meals.update(owner, dinner.getId(), timedMeal(DAY, LocalTime.of(18, 0), 60));
        assertDates(routine);
        assertEquals(0, evidenceCount(routine));
        meals.update(owner, dinner.getId(), timedMeal(DAY, LocalTime.of(18, 0), 30));
        assertDates(routine, DAY.plusDays(1));
        meals.delete(owner, breakfast.getId());
        assertDates(routine);
        assertEquals(0, evidenceCount(routine));
        assertSummary(owner, routine, 0, 0, null);
    }

    @Test void pausingAutomationPreservesHistoryAndManualUpdatesUntilReenabled() {
        var owner = user("pause-fruit");
        var routine = routine(owner, RoutineAutomaticTrigger.FRUIT_MEAL);
        var fruit = meals.create(owner, meal(DAY, true));
        routines.checkin(owner, routine.getId(), DateTimes.startOfDay(DAY.plusDays(1)).plusHours(10));
        var storedCheckins = jdbc.queryForList("select * from routine_checkins where routine_id = ? order by checked_at", routine.getId());
        var storedEvidence = jdbc.queryForList("select * from routine_automatic_evidence where routine_id = ?", routine.getId());
        toggle(owner, routine, false);
        assertEquals(storedCheckins, jdbc.queryForList("select * from routine_checkins where routine_id = ? order by checked_at", routine.getId()));
        meals.update(owner, fruit.getId(), meal(DAY.plusDays(2), false));
        automation.reconcile(owner);
        assertEquals(storedCheckins, jdbc.queryForList("select * from routine_checkins where routine_id = ? order by checked_at", routine.getId()));
        assertEquals(storedEvidence, jdbc.queryForList("select * from routine_automatic_evidence where routine_id = ?", routine.getId()));
        assertSummary(owner, routine, 2, 2, DAY.plusDays(1));
        routines.update(owner, routine.getId(), new RoutineRequest("Renamed fruit", new java.util.LinkedHashSet<>(Set.of(RoutineType.WEIGHT)), List.of(), false));
        var paused = routines.requireOwned(owner, routine.getId());
        assertFalse(paused.isAutomaticCompletionEnabled());
        assertEquals(RoutineAutomaticTrigger.FRUIT_MEAL, paused.getAutomaticTrigger());
        routines.checkin(owner, routine.getId(), DateTimes.startOfDay(DAY.plusDays(2)).plusHours(10));
        routines.undoCheckin(owner, routine.getId(), DateTimes.startOfDay(DAY.plusDays(2)).plusHours(10));
        assertDates(routine, DAY, DAY.plusDays(1));
        meals.delete(owner, fruit.getId());
        meals.create(owner, meal(DAY.plusDays(2), true));
        meals.create(owner, meal(DAY.plusDays(2), true));
        assertDates(routine, DAY, DAY.plusDays(1));
        toggle(owner, routine, true);
        assertDates(routine, DAY.plusDays(1), DAY.plusDays(2));
        assertEquals(2, evidenceCount(routine));
        assertSummary(owner, routine, 2, 2, DAY.plusDays(2));
        assertTrue(routines.requireOwned(owner, routine.getId()).isAutomaticCompletionEnabled());
        assertEquals(RoutineAutomaticTrigger.FRUIT_MEAL, routines.requireOwned(owner, routine.getId()).getAutomaticTrigger());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = RoutineAutomaticTrigger.class, names = "NONE", mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE)
    void everyAssignedRuleRetainsAutomaticHistoryWhenItsSourceIsDeletedWhilePaused(RoutineAutomaticTrigger trigger) {
        var owner = user("paused-" + trigger);
        var routine = routine(owner, trigger);
        Runnable deleteSource;
        if (trigger == RoutineAutomaticTrigger.FRUIT_MEAL || trigger == RoutineAutomaticTrigger.KIWI_MEAL) {
            var source = meals.create(owner, trigger == RoutineAutomaticTrigger.KIWI_MEAL ? meal(DAY, "Kiwi", false) : meal(DAY, true));
            deleteSource = () -> meals.delete(owner, source.getId());
        } else if (trigger == RoutineAutomaticTrigger.SLEEP_BED_AT_LEAST_7_HOURS || trigger == RoutineAutomaticTrigger.WAKE_BEFORE_7) {
            var source = sleeps.create(owner, sleep(DAY, LocalTime.of(6, 0), 8));
            deleteSource = () -> sleeps.delete(owner, source.getId());
        } else if (trigger == RoutineAutomaticTrigger.FAST_OVER_12_HOURS) {
            var start = OffsetDateTime.parse("2026-08-11T10:00:00Z");
            var source = fasts.create(owner, new FastingPeriodRequest(start, start.plusHours(13), null));
            deleteSource = () -> fasts.delete(owner, source.getId());
        } else {
            var exercise = switch (trigger) {
                case CARDIO_WORKOUT -> exercise("Paused cardio", ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO);
                case STRENGTH_WORKOUT -> exercise("Paused strength", ExerciseType.TRAINING, ExerciseTrackingMode.REPS);
                case STRETCHING_WORKOUT -> exercise("Paused stretch", ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS);
                case MCGILL_BIG_THREE -> exercises.findAll().stream().filter(candidate -> "mcgill-big-three".equals(candidate.getBuiltInImageKey())).findFirst().orElseThrow();
                case NIGHT_STRETCHING_WORKOUT -> exercise("Paused night stretch", ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS);
                case SITTING_RISING_WORKOUT -> sittingRisingExercise();
                default -> throw new AssertionError(trigger);
            };
            var source = workouts.create(owner, workout(DAY, List.of(line(exercise)), trigger == RoutineAutomaticTrigger.NIGHT_STRETCHING_WORKOUT ? LocalTime.of(19, 1) : null));
            deleteSource = () -> workouts.delete(owner, source.getId());
        }
        assertDates(routine, DAY);
        toggle(owner, routine, false);
        deleteSource.run();
        automation.reconcile(owner);
        assertDates(routine, DAY);
        assertEquals(1, evidenceCount(routine));
        assertSummary(owner, routine, 1, 1, DAY);
        toggle(owner, routine, true);
        assertDates(routine);
        assertEquals(0, evidenceCount(routine));
        assertSummary(owner, routine, 0, 0, null);
    }

    private void toggle(User owner, Routine routine, boolean enabled) {
        var saved = routines.requireOwned(owner, routine.getId());
        routines.update(owner, routine.getId(), new RoutineRequest(saved.getName(), saved.getTypes(), saved.getReminders().stream().map(RoutineReminder::getReminderTime).toList(), saved.getPersonalRecordsEnabled(), saved.getAutomaticTrigger(), enabled));
    }

    private User user(String name) {
        var user = new User(); user.setEmail(name + "@example.com"); user.setDashboardAnchorDate(DAY.minusDays(5)); return users.save(user);
    }
    private Routine routine(User owner, RoutineAutomaticTrigger trigger) {
        var routine = routines.create(owner, new RoutineRequest(trigger.name(), new java.util.LinkedHashSet<>(Set.of(RoutineType.MIND)), List.of(LocalTime.of(8, 0)), true));
        routine.setAutomaticTrigger(trigger);
        routine.setAutomaticCompletionEnabled(true);
        routineRepository.saveAndFlush(routine);
        jdbc.update("update routine_reminders set reminder_snoozed_until = ? where routine_id = ?", java.sql.Timestamp.from(DateTimes.startOfDay(DAY).plusHours(15).toInstant()), routine.getId());
        return routine;
    }
    private MealRequest meal(LocalDate date, boolean fruit) {
        return meal(date, "Apple", fruit);
    }
    private MealRequest meal(LocalDate date, String name, boolean fruit) {
        return new MealRequest(date, MealType.SNACK, 100, null, null, null, null, null,
            List.of(new MealDishRequest(name, 100, null, null, null, BigDecimal.ONE, DishUnit.UNIT, null, fruit, java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, "Test composition", false)), null);
    }
    private MealRequest timedMeal(LocalDate date, LocalTime time, int duration) {
        return new MealRequest(date, MealType.SNACK, 100, null, null, null, time, null, List.of(), duration);
    }
    private Exercise exercise(String name, ExerciseType type, ExerciseTrackingMode mode) {
        return exercises.create(new ExerciseRequest(name, "Acceptance exercise", mode, type, type == ExerciseType.TRAINING && mode != ExerciseTrackingMode.CARDIO ? PrimaryMuscleGroup.CORE : null));
    }
    private Exercise sittingRisingExercise() {
        return exercises.findAll().stream().filter(exercise -> exercise.getName().trim().equalsIgnoreCase("Sitting-Rising"))
            .findFirst().orElseGet(() -> exercise("Sitting-Rising", ExerciseType.TRAINING, ExerciseTrackingMode.REPS));
    }
    private WorkoutLineRequest line(Exercise exercise) {
        boolean reps = exercise.getTrackingMode() == ExerciseTrackingMode.REPS;
        return new WorkoutLineRequest(exercise.getId(), null, null,
            List.of(new WorkoutSegmentRequest(reps ? 10 : null, reps ? null : 60, null, null, null, null, null, null, null)), null);
    }
    private WorkoutRequest workout(LocalDate date, List<WorkoutLineRequest> lines) {
        return workout(date, lines, null);
    }
    private WorkoutRequest workout(LocalDate date, List<WorkoutLineRequest> lines, LocalTime start) {
        return new WorkoutRequest(date, null, lines, start, null, null, null, null, null);
    }
    private SleepRequest sleep(LocalDate date, LocalTime end, int elapsedHours) {
        OffsetDateTime bedtimeEnd = date.atTime(end).atZone(DateTimes.USER_ZONE).toOffsetDateTime();
        return new SleepRequest(date, bedtimeEnd.minusHours(elapsedHours), bedtimeEnd, 10800, 3600, 3600, 3600, 0, BigDecimal.valueOf(50), 40);
    }
    private void assertDates(Routine routine, LocalDate... dates) {
        var persisted = checkins.findByRoutineOrderByCheckedAtAsc(routine).stream().map(checkin -> DateTimes.toLocalDate(checkin.getCheckedAt())).toList();
        assertEquals(List.of(dates), persisted);
    }
    private int evidenceCount(Routine routine) {
        return jdbc.queryForObject("select count(*) from routine_automatic_evidence where routine_id = ?", Integer.class, routine.getId());
    }
    private void assertEvidence(Routine routine, String kind, String key, LocalDate date) {
        assertEquals(date, jdbc.queryForObject("select event_date from routine_automatic_evidence where routine_id = ? and source_kind = ? and source_key = ?", LocalDate.class, routine.getId(), kind, key));
    }
    private void assertSummary(User owner, Routine routine, int current, int best, LocalDate lastDate) {
        var loaded = routines.requireOwned(owner, routine.getId());
        assertEquals(current, loaded.getCurrentStrike()); assertEquals(best, loaded.getBestStrike());
        assertEquals(lastDate, loaded.getLastTimeDate() == null ? null : DateTimes.toLocalDate(loaded.getLastTimeDate()));
    }
}
