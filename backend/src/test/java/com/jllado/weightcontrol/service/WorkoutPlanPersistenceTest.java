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
    @Autowired ObjectMapper json;

    @Test void persistsSnapshotsAndArchivesOnlyOnNewCommitment() {
        var owner = user(); var other = user();
        var exercise = exercise(ExerciseTrackingMode.REPS, ExerciseType.TRAINING);
        var request = week(List.of(new WorkoutPlanLineRequest(exercise.getId(), List.of(reps(8)))));
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
        assertEquals(edited.days(), archived.days());
        assertEquals(next.id(), service.current(owner).orElseThrow().id());
        assertEquals(first.id(), service.archive(owner, 0, 1).items().getFirst().id());
        assertThrows(BadRequestException.class, () -> service.update(owner, first.id(), new WorkoutPlanUpdateRequest(request, edited.updateToken())));
        assertThrows(ResponseStatusException.class, () -> service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(request, edited.updateToken(), true)));
        var changed = week(List.of(new WorkoutPlanLineRequest(exercise.getId(), List.of(reps(10)))));
        var saved = service.updateConfirmed(owner, new CoachWorkoutPlanUpdateRequest(changed, next.updateToken(), true));
        assertEquals(10, saved.days().getFirst().lines().getFirst().segments().getFirst().repetitions());
        assertEquals(next.days().subList(1, 7), saved.days().subList(1, 7));
        assertEquals(saved.days(), service.get(owner, saved.id()).days());
        assertEquals(0, jdbc.queryForObject("select count(*) from workouts where user_id = ?", Integer.class, owner.getId()));
    }

    @Test void validatesTargetsAndRollsBackFailedReplacement() {
        var owner = user();
        var training = exercise(ExerciseTrackingMode.REPS, ExerciseType.TRAINING);
        var timed = exercise(ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING);
        var cardio = exercise(ExerciseTrackingMode.CARDIO, ExerciseType.WARM_UP);
        var stretch = exercise(ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING);
        var lines = List.of(
            new WorkoutPlanLineRequest(training.getId(), List.of(reps(8), reps(10))),
            new WorkoutPlanLineRequest(timed.getId(), List.of(new WorkoutSegmentRequest(null, 65, BigDecimal.ONE, null, null, null, null, null))),
            new WorkoutPlanLineRequest(cardio.getId(), List.of(new WorkoutSegmentRequest(null, 600, null, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, 2, null))),
            new WorkoutPlanLineRequest(stretch.getId(), List.of(new WorkoutSegmentRequest(null, 35, null, null, null, null, null, null))));
        var valid = week(lines);
        assertTrue(validator.validate(valid).isEmpty());
        var plan = service.create(owner, valid);
        for (var invalid : List.of(
            week(List.of(new WorkoutPlanLineRequest(training.getId(), List.of(reps(0))))),
            week(List.of(lines.getFirst(), lines.getFirst())),
            week(List.of(new WorkoutPlanLineRequest(stretch.getId(), List.of(new WorkoutSegmentRequest(null, 32, null, null, null, null, null, null))))),
            week(List.of(new WorkoutPlanLineRequest(cardio.getId(), List.of(reps(10))))),
            new WorkoutPlanRequest(valid.startDate(), valid.startDate().minusDays(1), null, valid.days()),
            new WorkoutPlanRequest(valid.startDate(), valid.reviewDate(), null, Collections.nCopies(7, valid.days().getFirst())))) {
            assertThrows(BadRequestException.class, () -> service.create(owner, invalid));
            assertEquals(plan.id(), service.current(owner).orElseThrow().id());
            assertTrue(service.archive(owner, 0, 10).items().isEmpty());
        }
        assertFalse(validator.validate(week(List.of(new WorkoutPlanLineRequest(training.getId(), List.of())))).isEmpty());
        assertFalse(validator.validate(new WorkoutPlanRequest(null, null, null, List.of())).isEmpty());
        for (Boolean confirmation : Arrays.asList(false, null)) {
            var request = new CoachWorkoutPlanUpdateRequest(valid, plan.updateToken(), confirmation);
            assertFalse(validator.validate(request).isEmpty());
            assertThrows(BadRequestException.class, () -> service.updateConfirmed(owner, request));
        }
        assertEquals(plan.updateToken(), service.current(owner).orElseThrow().updateToken());
        assertThrows(BadRequestException.class, () -> service.archive(owner, -1, 10));
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
        var plan = service.create(owner, week(List.of(new WorkoutPlanLineRequest(exercise(ExerciseTrackingMode.REPS, ExerciseType.TRAINING).getId(), List.of(reps(8))))));
        var result = context.getHealthContext(owner, date, date, Set.of(CoachDomain.WORKOUT_PLAN), now);
        assertEquals(Set.of(CoachDomain.WORKOUT_PLAN), result.data().keySet());
        var schedule = ((CoachDtos.WorkoutPlanContext) result.data().get(CoachDomain.WORKOUT_PLAN)).plan();
        assertEquals(plan.startDate(), schedule.startDate());
        var text = json.writeValueAsString(result);
        for (String privateField : List.of("exerciseId", "updateToken", "imageUrl", "user_id", "email")) assertFalse(text.contains(privateField));
        assertEquals(plan.updateToken(), service.editContext(owner).plan().updateToken());
        assertFalse(service.editContext(owner).exercises().isEmpty());
    }
    private User user() { var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); return users.save(user); }
    private Exercise exercise(ExerciseTrackingMode mode, ExerciseType type) { return exercises.create(new ExerciseRequest("Plan exercise " + UUID.randomUUID(), "Instructions", mode, type)); }
    private WorkoutSegmentRequest reps(int count) { return new WorkoutSegmentRequest(count, null, BigDecimal.TEN, null, null, null, null, null); }
    private WorkoutPlanRequest week(List<WorkoutPlanLineRequest> lines) {
        var days = Arrays.stream(DayOfWeek.values()).map(day -> new WorkoutPlanDayRequest(day, day != DayOfWeek.MONDAY || lines.isEmpty(), day == DayOfWeek.TUESDAY ? "Recovery" : null, day == DayOfWeek.MONDAY ? lines : List.of())).toList();
        return new WorkoutPlanRequest(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 26), "Six-week commitment", days);
    }
}
