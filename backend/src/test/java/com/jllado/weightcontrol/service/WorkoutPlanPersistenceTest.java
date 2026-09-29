package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.CoachDtos;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-30-bytes-long"})
class WorkoutPlanPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("workout_plans"); }
    }
    @Autowired WorkoutPlanService service;
    @Autowired ExerciseService exercises;
    @Autowired UserRepository users;
    @Autowired Validator validator;
    @Autowired JdbcTemplate jdbc;
    @Autowired HealthDataContextService context;
    @Autowired WorkoutService workouts;
    @Autowired ObjectMapper json;

    @Test void preservesSupersetMembershipInPlansAndCoachContext() throws Exception {
        var owner = user();
        var first = exercise(ExerciseTrackingMode.REPS, ExerciseType.TRAINING);
        var second = exercise(ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING);
        var groupId = UUID.randomUUID().toString();
        var plan = service.create(owner, week(List.of(
            new WorkoutPlanLineRequest(first.getId(), List.of(reps(8), reps(8)), null, groupId),
            new WorkoutPlanLineRequest(second.getId(), List.of(new WorkoutSegmentRequest(null, 30, null, null, null, null, null, null, null), new WorkoutSegmentRequest(null, 30, null, null, null, null, null, null, null)), StretchingUnit.SECONDS, groupId)
        )));

        var lines = service.get(owner, plan.id()).days().getFirst().sessions().getFirst().lines();
        assertEquals(List.of(groupId, groupId), lines.stream().map(WorkoutPlanDay.Target::supersetGroupId).toList());
        var coachPlan = CoachDtos.PlannedWeek.from(plan).days().getFirst().lines();
        assertEquals(List.of(groupId, groupId), coachPlan.stream().map(CoachDtos.PlannedExercise::supersetGroupId).toList());
        assertThrows(BadRequestException.class, () -> service.create(owner, week(List.of(
            new WorkoutPlanLineRequest(first.getId(), List.of(reps(8), reps(8)), null, groupId),
            new WorkoutPlanLineRequest(second.getId(), List.of(new WorkoutSegmentRequest(null, 30, null, null, null, null, null, null, null)), StretchingUnit.SECONDS, groupId)
        ))));
    }

    @Test void preservesBreathsInCoachReplacementArchivesAndLegacySnapshots() throws Exception {
        var owner = user(); var stretch = exercise(ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING);
        var hold = new WorkoutSegmentRequest(null, null, null, null, null, null, null, null, 6);
        var request = week(List.of(new WorkoutPlanLineRequest(stretch.getId(), List.of(hold), StretchingUnit.BREATHS)));
        var plan = service.create(owner, request);
        var edited = service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(request, plan.updateToken(), true, 1));
        assertEquals(6, service.get(owner, plan.id()).days().getFirst().lines().getFirst().segments().getFirst().breaths());
        var coach = CoachDtos.PlannedWeek.from(edited).days().getFirst().lines().getFirst();
        assertEquals(StretchingUnit.BREATHS, coach.stretchingUnit());
        assertEquals(6, coach.segments().getFirst().breaths());
        service.create(owner, week(List.of()));
        assertEquals(edited.days(), service.get(owner, plan.id()).days());
        var legacy = json.readValue("{\"exerciseId\":1,\"exerciseName\":\"Stretch\",\"exerciseDescription\":\"Hold\",\"trackingMode\":\"SECONDS\",\"exerciseType\":\"STRETCHING\",\"segments\":[{\"durationSeconds\":30}]}", WorkoutPlanDay.Target.class);
        assertEquals(StretchingUnit.SECONDS, legacy.stretchingUnit());
        assertEquals(30, legacy.segments().getFirst().durationSeconds());
        assertNull(legacy.segments().getFirst().breaths());
    }

    @Test void persistsSnapshotsAndArchivesOnlyOnNewCommitment() {
        var owner = user(); var other = user();
        var exercise = exercise(ExerciseTrackingMode.REPS, ExerciseType.TRAINING);
        var request = week(List.of(new WorkoutPlanLineRequest(exercise.getId(), List.of(reps(8)), null)));
        var first = service.create(owner, request);
        assertEquals(request.startDate(), service.current(owner).orElseThrow().startDate());
        assertEquals(7, first.days().size());
        assertTrue(service.current(other).isEmpty());
        assertTrue(service.archive(other, 0, 10).items().isEmpty());
        assertThrows(NotFoundException.class, () -> service.get(other, first.id()));
        assertThrows(NotFoundException.class, () -> service.update(other, first.id(), new WorkoutPlanUpdateRequest(request, first.updateToken())));
        String originalName = first.days().getFirst().lines().getFirst().exerciseName();
        exercises.update(exercise.getId(), new ExerciseRequest("Renamed " + UUID.randomUUID(), "New description", ExerciseTrackingMode.SECONDS, ExerciseType.WARM_UP));
        var edited = service.update(owner, first.id(), new WorkoutPlanUpdateRequest(request, first.updateToken()));
        assertEquals(originalName, edited.days().getFirst().lines().getFirst().exerciseName());
        assertEquals(ExerciseTrackingMode.REPS, edited.days().getFirst().lines().getFirst().trackingMode());
        assertTrue(service.archive(owner, 0, 10).items().isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.update(owner, first.id(), new WorkoutPlanUpdateRequest(request, first.updateToken())));
        exercises.delete(exercise.getId());
        var next = service.create(owner, request);
        var archived = service.get(owner, first.id());
        assertNotNull(archived.archivedAt());
        assertEquals(edited.days().getFirst().sessions().getFirst().lines().getFirst().segments().getFirst().repetitions(), archived.days().getFirst().sessions().getFirst().lines().getFirst().segments().getFirst().repetitions());
        assertEquals(next.id(), service.current(owner).orElseThrow().id());
        assertEquals(first.id(), service.archive(owner, 0, 1).items().getFirst().id());
        assertThrows(BadRequestException.class, () -> service.update(owner, first.id(), new WorkoutPlanUpdateRequest(request, edited.updateToken())));
        assertThrows(ResponseStatusException.class, () -> service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(request, edited.updateToken(), true, 1)));
        var changed = week(List.of(new WorkoutPlanLineRequest(exercise.getId(), List.of(reps(10)), null)));
        var saved = service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(changed, next.updateToken(), true, 1));
        assertEquals(10, saved.days().getFirst().lines().getFirst().segments().getFirst().repetitions());
        assertEquals(next.days().subList(1, 7), saved.days().subList(1, 7));
        var reloaded = service.get(owner, saved.id());
        assertEquals(saved.days().size(), reloaded.days().size());
        assertEquals(10, reloaded.days().getFirst().sessions().getFirst().lines().getFirst().segments().getFirst().repetitions());
        assertEquals("Recovery", reloaded.days().get(1).note());
        assertEquals(0, jdbc.queryForObject("select count(*) from workouts where user_id = ?", Integer.class, owner.getId()));
    }

    @Test void validatesTargetsAndRollsBackFailedReplacement() {
        var owner = user();
        var training = exercise(ExerciseTrackingMode.REPS, ExerciseType.TRAINING);
        var timed = exercise(ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING);
        var cardio = exercise(ExerciseTrackingMode.CARDIO, ExerciseType.WARM_UP);
        var stretch = exercise(ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING);
        var lines = List.of(
            new WorkoutPlanLineRequest(training.getId(), List.of(reps(8), reps(10)), null),
            new WorkoutPlanLineRequest(timed.getId(), List.of(new WorkoutSegmentRequest(null, 65, BigDecimal.ONE, null, null, null, null, null, null)), null),
            new WorkoutPlanLineRequest(cardio.getId(), List.of(new WorkoutSegmentRequest(null, 600, null, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, 2, null, null)), null),
            new WorkoutPlanLineRequest(stretch.getId(), List.of(new WorkoutSegmentRequest(null, 35, null, null, null, null, null, null, null)), null));
        var valid = week(lines);
        assertTrue(validator.validate(valid).isEmpty());
        var plan = service.create(owner, valid);
        for (var invalid : List.of(
            week(List.of(new WorkoutPlanLineRequest(training.getId(), List.of(reps(0)), null))),
            week(List.of(lines.getFirst(), lines.getFirst())),
            week(List.of(new WorkoutPlanLineRequest(stretch.getId(), List.of(new WorkoutSegmentRequest(null, 32, null, null, null, null, null, null, null)), null))),
            week(List.of(new WorkoutPlanLineRequest(cardio.getId(), List.of(reps(10)), null))),
            new WorkoutPlanRequest(valid.startDate(), valid.startDate().minusDays(1), null, valid.days()),
            new WorkoutPlanRequest(valid.startDate(), valid.reviewDate(), null, Collections.nCopies(7, valid.days().getFirst())))) {
            assertThrows(BadRequestException.class, () -> service.create(owner, invalid));
            assertEquals(plan.id(), service.current(owner).orElseThrow().id());
            assertTrue(service.archive(owner, 0, 10).items().isEmpty());
        }
        assertFalse(validator.validate(week(List.of(new WorkoutPlanLineRequest(training.getId(), List.of(), null)))).isEmpty());
        assertFalse(validator.validate(new WorkoutPlanRequest(null, null, null, List.of())).isEmpty());
        for (Boolean confirmation : Arrays.asList(false, null)) {
            var request = new CoachWorkoutPlanUpdateRequest(valid, plan.updateToken(), confirmation, 1);
            assertFalse(validator.validate(request).isEmpty());
            assertThrows(BadRequestException.class, () -> service.updateConfirmed(owner, request));
        }
        assertEquals(plan.updateToken(), service.current(owner).orElseThrow().updateToken());
        assertThrows(BadRequestException.class, () -> service.archive(owner, -1, 10));
    }

    @Test void identifiesWeekdayAndExerciseWhenPlanTargetsAreInvalid() {
        var owner = user();
        var treadmill = exercises.create(new ExerciseRequest("Treadmill " + UUID.randomUUID(), "Intervals", ExerciseTrackingMode.CARDIO, ExerciseType.WARM_UP));
        var cadenceTarget = new WorkoutPlanLineRequest(treadmill.getId(), List.of(
            new WorkoutSegmentRequest(null, 600, null, null, null, null, null, null, null, BigDecimal.ONE)
        ), null);

        var error = assertThrows(BadRequestException.class, () -> service.create(owner, week(List.of(cadenceTarget))));

        assertEquals("MONDAY — " + treadmill.getName() + ": Only elliptical intervals use cadence in RPM", error.getMessage());
    }

    @Test void storesIndependentSessionsReadsLegacyDaysAndSnapshotsRecordingTargets() {
        var owner = user();
        var exercise = exercise(ExerciseTrackingMode.REPS, ExerciseType.TRAINING);
        var target = new WorkoutPlanLineRequest(exercise.getId(), List.of(reps(8)), null);
        var request = week(List.of());
        var days = new ArrayList<>(request.days());
        days.set(0, new WorkoutPlanDayRequest(DayOfWeek.MONDAY, false, null, null, List.of(
            new WorkoutPlanSessionRequest(null, "Early", List.of(target)),
            new WorkoutPlanSessionRequest("Evening", "Second round", List.of(target))
        )));
        var plan = service.create(owner, new WorkoutPlanRequest(request.startDate(), request.reviewDate(), request.notes(), days));
        var originalExerciseName = plan.days().getFirst().sessions().getFirst().lines().getFirst().exerciseName();
        assertEquals(2, plan.days().getFirst().sessions().size());
        assertNull(plan.days().getFirst().sessions().getFirst().name());
        assertEquals("Evening", plan.days().getFirst().sessions().get(1).name());
        assertFalse(json.valueToTree(plan).path("days").get(0).has("lines"));

        exercises.update(exercise.getId(), new ExerciseRequest("Renamed after plan", "Changed", ExerciseTrackingMode.SECONDS, ExerciseType.WARM_UP));
        var edited = service.update(owner, plan.id(), new WorkoutPlanUpdateRequest(new WorkoutPlanRequest(request.startDate(), request.reviewDate(), request.notes(), days), plan.updateToken()));
        assertEquals(originalExerciseName, edited.days().getFirst().sessions().get(1).lines().getFirst().exerciseName());
        assertEquals(8, edited.days().getFirst().sessions().getFirst().lines().getFirst().segments().getFirst().repetitions());

        var legacy = new WorkoutPlanDaysJsonConverter().convertToEntityAttribute("[{\"day\":\"MONDAY\",\"rest\":false,\"note\":\"Old note\",\"lines\":[{\"exerciseId\":1,\"exerciseName\":\"Old exercise\",\"exerciseDescription\":\"Old description\",\"trackingMode\":\"REPS\",\"exerciseType\":\"TRAINING\",\"segments\":[{\"repetitions\":8}]}]}]");
        assertEquals(1, legacy.getFirst().sessions().size());
        assertNull(legacy.getFirst().sessions().getFirst().name());
        assertEquals("Old note", legacy.getFirst().sessions().getFirst().note());

        var recordedExercise = exercise(ExerciseTrackingMode.REPS, ExerciseType.TRAINING);
        var recordTarget = new PlannedTargetRequest("Planned press", "Snapshot detail", ExerciseTrackingMode.REPS, ExerciseType.TRAINING, null, StretchingUnit.SECONDS,
            List.of(new WorkoutSegmentRequest(8, null, BigDecimal.valueOf(20), null, null, null, null, null, null)));
        var workout = workouts.create(owner, new WorkoutRequest(LocalDate.now().minusDays(1), null,
            List.of(new WorkoutLineRequest(recordedExercise.getId(), null, null, List.of(reps(10)), null)), null, null, null, null, null, null,
            "Upper body", List.of(recordTarget)));
        exercises.update(exercise.getId(), new ExerciseRequest("Current plan exercise", "Current instructions", ExerciseTrackingMode.REPS, ExerciseType.TRAINING));
        days.set(0, new WorkoutPlanDayRequest(DayOfWeek.MONDAY, false, null, null, List.of(
            new WorkoutPlanSessionRequest("Renamed session", "Edited later", List.of(new WorkoutPlanLineRequest(exercise.getId(), List.of(reps(12)), null)))
        )));
        var revisedPlan = service.update(owner, plan.id(), new WorkoutPlanUpdateRequest(
            new WorkoutPlanRequest(request.startDate(), request.reviewDate(), request.notes(), days), edited.updateToken()));
        assertEquals("Renamed session", revisedPlan.days().getFirst().sessions().getFirst().name());
        assertEquals(12, revisedPlan.days().getFirst().sessions().getFirst().lines().getFirst().segments().getFirst().repetitions());
        exercises.update(recordedExercise.getId(), new ExerciseRequest("Current press", "Current catalog", ExerciseTrackingMode.REPS, ExerciseType.TRAINING));
        var reloaded = workouts.requireOwned(owner, workout.getId());
        assertEquals("Upper body", reloaded.getPlannedSessionName());
        assertEquals("Planned press", reloaded.getPlannedTargets().getFirst().exerciseName());
        assertEquals(8, reloaded.getPlannedTargets().getFirst().segments().getFirst().repetitions());
        assertEquals(10, reloaded.getLines().getFirst().getSegments().getFirst().getRepetitions());
    }

    @Test void coachCanCreateFirstPlanAfterConfirmationAndLegacyRestNotesSurviveCanonicalRead() {
        var owner = user();
        var request = week(List.of());
        assertThrows(BadRequestException.class, () -> service.updateConfirmed(user(), new CoachWorkoutPlanUpdateRequest(request, "unexpected-token", true, 1)));
        var created = service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(request, null, true, 1));
        assertEquals(created.id(), service.current(owner).orElseThrow().id());
        assertThrows(ResponseStatusException.class, () -> service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(request, "unexpected-token", true, 1)));
        assertThrows(BadRequestException.class, () -> service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(request, null, false, 1)));

        var legacy = new WorkoutPlanDaysJsonConverter().convertToEntityAttribute("[{\"day\":\"MONDAY\",\"rest\":true,\"note\":\"Recovery\",\"lines\":[]}]");
        assertTrue(legacy.getFirst().sessions().isEmpty());
        assertEquals("Recovery", legacy.getFirst().note());
    }

    @Test void concurrentNewPlansLeaveOneCurrentAndOneArchive() throws Exception {
        var owner = user(); var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<WorkoutPlanResponse> create = () -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return service.create(owner, week(List.of())); };
            var first = pool.submit(create); var second = pool.submit(create);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            var a = first.get(20, TimeUnit.SECONDS); var b = second.get(20, TimeUnit.SECONDS);
            assertNotEquals(a.id(), b.id());
            assertEquals(1, service.archive(owner, 0, 10).totalElements());
            assertEquals(1, jdbc.queryForObject("select count(*) from workout_plans where user_id = ? and archived_at is null", Integer.class, owner.getId()));
        }
    }

    @Test void coachContextExposesCurrentIntentWithoutIdentifiersOrHistoricalClaims() throws Exception {
        var owner = user(); var date = LocalDate.of(2026, 1, 1); var now = OffsetDateTime.now();
        var empty = context.getHealthContext(owner, date, date, Set.of(CoachDomain.WORKOUT_PLAN), now);
        assertNull(((CoachDtos.WorkoutPlanContext) empty.data().get(CoachDomain.WORKOUT_PLAN)).plan());
        assertNull(service.editContext(owner).plan());
        var plan = service.create(owner, week(List.of(new WorkoutPlanLineRequest(exercise(ExerciseTrackingMode.REPS, ExerciseType.TRAINING).getId(), List.of(reps(8)), null))));
        var result = context.getHealthContext(owner, date, date, Set.of(CoachDomain.WORKOUT_PLAN), now);
        assertEquals(Set.of(CoachDomain.WORKOUT_PLAN), result.data().keySet());
        var schedule = ((CoachDtos.WorkoutPlanContext) result.data().get(CoachDomain.WORKOUT_PLAN)).plan();
        assertEquals(plan.startDate(), schedule.startDate());
        var text = json.writeValueAsString(result);
        for (String privateField : List.of("exerciseId", "updateToken", "imageUrl", "user_id", "email")) assertFalse(text.contains(privateField));
        assertEquals(plan.updateToken(), service.editContext(owner).plan().updateToken());
        assertFalse(service.editContext(owner).exercises().isEmpty());
    }

    @Test void saunaOnlyAndMixedSessionsRoundTripAndOldCoachWritesCannotEraseThem() {
        var owner = user();
        var stretch = exercise(ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING);
        var base = week(List.of());
        var days = new ArrayList<>(base.days());
        var sauna = new WorkoutPlanSessionRequest("Sauna", null, List.of(), true, List.of(12, 8));
        var mixed = new WorkoutPlanSessionRequest("Stretch and sauna", null,
            List.of(new WorkoutPlanLineRequest(stretch.getId(), List.of(new WorkoutSegmentRequest(null, 30, null, null, null, null, null, null, null)), null)), true, List.of(10));
        days.set(0, new WorkoutPlanDayRequest(DayOfWeek.MONDAY, false, null, null, List.of(sauna, mixed)));
        var request = new WorkoutPlanRequest(base.startDate(), base.reviewDate(), base.notes(), days);
        var saved = service.create(owner, request);
        assertEquals(List.of(12, 8), service.current(owner).orElseThrow().days().getFirst().sessions().getFirst().saunaRoundsMinutes());
        var coach = context.getHealthContext(owner, base.startDate(), base.startDate(), Set.of(CoachDomain.WORKOUT_PLAN), OffsetDateTime.now());
        var planned = ((CoachDtos.WorkoutPlanContext) coach.data().get(CoachDomain.WORKOUT_PLAN)).plan().days().getFirst().sessions();
        assertTrue(planned.getFirst().saunaSession());
        assertEquals(List.of(10), planned.get(1).saunaRoundsMinutes());

        var allRest = base;
        var oldSchema = new CoachWorkoutPlanUpdateRequest(allRest, saved.updateToken(), true, null);
        assertFalse(validator.validate(oldSchema).isEmpty());
        assertThrows(BadRequestException.class, () -> service.updateConfirmed(owner, oldSchema));
        assertEquals(List.of(12, 8), service.current(owner).orElseThrow().days().getFirst().sessions().getFirst().saunaRoundsMinutes());
        var updated = service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(request, saved.updateToken(), true, 1));
        assertEquals(2, updated.days().getFirst().sessions().size());
        for (var invalid : List.of(new WorkoutPlanSessionRequest("Invalid", null, List.of(), true, List.of()),
            new WorkoutPlanSessionRequest("Invalid", null, List.of(), true, List.of(0)),
            new WorkoutPlanSessionRequest("Invalid", null, List.of(), false, List.of(5)))) {
            days.set(0, new WorkoutPlanDayRequest(DayOfWeek.MONDAY, false, null, null, List.of(invalid)));
            var invalidRequest = new WorkoutPlanRequest(base.startDate(), base.reviewDate(), base.notes(), days);
            assertThrows(BadRequestException.class, () -> service.create(owner, invalidRequest));
        }
        var cleared = service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(allRest, updated.updateToken(), true, 1));
        assertTrue(cleared.days().stream().allMatch(WorkoutPlanDay::rest));
    }
    private User user() { var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); return users.save(user); }
    private Exercise exercise(ExerciseTrackingMode mode, ExerciseType type) { return exercises.create(new ExerciseRequest("Plan exercise " + UUID.randomUUID(), "Instructions", mode, type)); }
    private WorkoutSegmentRequest reps(int count) { return new WorkoutSegmentRequest(count, null, BigDecimal.TEN, null, null, null, null, null, null); }
    private WorkoutPlanRequest week(List<WorkoutPlanLineRequest> lines) {
        var days = Arrays.stream(DayOfWeek.values()).map(day -> new WorkoutPlanDayRequest(day, day != DayOfWeek.MONDAY || lines.isEmpty(), day == DayOfWeek.TUESDAY ? "Recovery" : null, day == DayOfWeek.MONDAY ? lines : List.of())).toList();
        return new WorkoutPlanRequest(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 26), "Six-week commitment", days);
    }
}
