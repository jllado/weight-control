package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest;
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
    @Autowired RoutineAutomationService automation;
    @Autowired MealService meals;
    @Autowired WorkoutService workouts;
    @Autowired ExerciseService exercises;
    @Autowired FastingPeriodService fasts;
    @Autowired UserRepository users;
    @Autowired RoutineCheckinRepository checkins;
    @Autowired JdbcTemplate jdbc;

    private static final LocalDate DAY = LocalDate.of(2026, 8, 12);

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

    @Test void triggerChangesRebuildOnlyAutomaticCheckinsAndLeaveUnconfiguredRoutinesUntouched() {
        var owner = user("trigger-changes");
        var legacy = routines.create(owner, new RoutineRequest("Legacy routine", Set.of(RoutineType.MIND), List.of(LocalTime.of(8, 0)), true));
        routines.checkin(owner, legacy.getId(), DateTimes.startOfDay(DAY).plusHours(10));
        jdbc.update("update routine_reminders set reminder_snoozed_until = ? where routine_id = ?", java.sql.Timestamp.from(DateTimes.startOfDay(DAY).plusHours(15).toInstant()), legacy.getId());
        var legacySummary = jdbc.queryForMap("select current_strike, best_strike, last_time_date, updated_at from routines where id = ?", legacy.getId());
        var routine = routine(owner, RoutineAutomaticTrigger.FRUIT_MEAL);
        var meal = meals.create(owner, meal(DAY, true));
        routines.checkin(owner, routine.getId(), DateTimes.startOfDay(DAY.plusDays(1)).plusHours(10));
        var cardio = exercise("Trigger change run", ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO);
        var workout = workouts.create(owner, workout(DAY.plusDays(2), List.of(line(cardio))));
        routines.update(owner, routine.getId(), request(RoutineAutomaticTrigger.CARDIO_WORKOUT));
        assertDates(routine, DAY.plusDays(1), DAY.plusDays(2));
        assertEquals(1, evidenceCount(routine));
        assertEvidence(routine, "WORKOUT", workout.getId().toString(), DAY.plusDays(2));
        assertSummary(owner, routine, 2, 2, DAY.plusDays(2));
        routines.update(owner, routine.getId(), request(RoutineAutomaticTrigger.NONE));
        assertDates(routine, DAY.plusDays(1));
        assertEquals(0, evidenceCount(routine));
        meals.delete(owner, meal.getId());
        workouts.delete(owner, workout.getId());
        assertDates(routine, DAY.plusDays(1));
        assertSummary(owner, routine, 1, 1, DAY.plusDays(1));
        assertDates(legacy, DAY);
        assertEquals(RoutineAutomaticTrigger.NONE, routines.requireOwned(owner, legacy.getId()).getAutomaticTrigger());
        assertEquals(legacySummary, jdbc.queryForMap("select current_strike, best_strike, last_time_date, updated_at from routines where id = ?", legacy.getId()));
        assertNotNull(jdbc.queryForObject("select reminder_snoozed_until from routine_reminders where routine_id = ?", java.sql.Timestamp.class, legacy.getId()));
    }

    private User user(String name) {
        var user = new User(); user.setEmail(name + "@example.com"); user.setDashboardAnchorDate(DAY.minusDays(5)); return users.save(user);
    }
    private Routine routine(User owner, RoutineAutomaticTrigger trigger) {
        var routine = routines.create(owner, request(trigger));
        jdbc.update("update routine_reminders set reminder_snoozed_until = ? where routine_id = ?", java.sql.Timestamp.from(DateTimes.startOfDay(DAY).plusHours(15).toInstant()), routine.getId());
        return routine;
    }
    private RoutineRequest request(RoutineAutomaticTrigger trigger) {
        return new RoutineRequest(trigger.name(), new java.util.LinkedHashSet<>(Set.of(RoutineType.MIND)), List.of(LocalTime.of(8, 0)), true, trigger);
    }
    private MealRequest meal(LocalDate date, boolean fruit) {
        return new MealRequest(date, MealType.SNACK, 100, null, null, null, null, null,
            List.of(new MealDishRequest("Apple", 100, null, null, null, BigDecimal.ONE, DishUnit.UNIT, null, fruit)), null);
    }
    private MealRequest timedMeal(LocalDate date, LocalTime time, int duration) {
        return new MealRequest(date, MealType.SNACK, 100, null, null, null, time, null, List.of(), duration);
    }
    private Exercise exercise(String name, ExerciseType type, ExerciseTrackingMode mode) {
        return exercises.create(new ExerciseRequest(name, "Acceptance exercise", mode, type));
    }
    private WorkoutLineRequest line(Exercise exercise) {
        boolean reps = exercise.getTrackingMode() == ExerciseTrackingMode.REPS;
        return new WorkoutLineRequest(exercise.getId(), null, null,
            List.of(new WorkoutSegmentRequest(reps ? 10 : null, reps ? null : 60, null, null, null, null, null, null, null)), null);
    }
    private WorkoutRequest workout(LocalDate date, List<WorkoutLineRequest> lines) {
        return new WorkoutRequest(date, null, lines, null, null, null, null, null, null);
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
