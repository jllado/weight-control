package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentWorkoutData;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true", "app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-30-bytes-long"})
class WorkoutTimingPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("workout_timing"); }
    }
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired WorkoutService service;
    @Autowired ExerciseService exercises;
    @Autowired UserRepository users;
    @Autowired Validator validator;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired HealthDataContextService context;
    @Autowired WeeklyMetricsCalculator metrics;
    @Autowired WorkoutAssessmentService assessments;
    @Autowired com.jllado.weightcontrol.repository.CoachingPlanRepository plans;
    @Autowired com.jllado.weightcontrol.repository.WorkoutAssessmentRepository ratings;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test void persistsCalculatesAndClearsSessionTimingWithoutChangingExerciseDurations() throws Exception {
        var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); user = users.save(user);
        var exercise = exercises.create(new ExerciseRequest("Timing " + UUID.randomUUID(), "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING));
        var lines = List.of(new WorkoutLineRequest(exercise.getId(), null, null, List.of(new WorkoutSegmentRequest(null, 30, BigDecimal.ZERO, null, null, null, null, null, null)), null));
        var date = LocalDate.of(2026, 8, 20);
        var legacy = json.readValue("{\"workoutDate\":\"2026-08-20\",\"lines\":[]}", WorkoutRequest.class);
        assertNull(legacy.startTime()); assertNull(legacy.durationMinutes());
        var saved = service.create(user, new WorkoutRequest(date, null, lines, null, null, null, null, null, null));
        assertNull(service.requireOwned(user, saved.getId()).getDurationMinutes());
        assertNull(jdbc.queryForObject("select start_time from workouts where id = ?", String.class, saved.getId()));

        service.update(user, saved.getId(), new WorkoutRequest(date, null, lines, LocalTime.MIDNIGHT, 1, 10, 45, 0, null));
        var loaded = service.requireOwned(user, saved.getId());
        assertEquals(LocalTime.MIDNIGHT, loaded.getStartTime());
        assertEquals(55, loaded.getDurationMinutes());
        assertEquals(0, loaded.getStretchingMinutes());
        assertEquals(30, loaded.getLines().getFirst().getSegments().getFirst().getDurationSeconds());
        var response = json.readTree(json.writeValueAsString(WorkoutResponse.from(loaded)));
        assertEquals("00:00", response.get("startTime").asText());
        assertEquals(55, response.get("durationMinutes").asInt());
        var comparable = AssessmentWorkoutData.comparable(loaded, Set.of());
        assertTrue(comparable.lines().isEmpty());
        assertEquals(55, comparable.durationMinutes());
        assertEquals(10, comparable.warmUpMinutes());
        assertEquals(45, AssessmentWorkoutData.from(loaded).trainingMinutes());

        service.update(user, saved.getId(), new WorkoutRequest(date, null, lines, null, 40, null, null, null, null));
        loaded = service.requireOwned(user, saved.getId());
        assertNull(loaded.getStartTime()); assertNull(loaded.getWarmUpMinutes()); assertEquals(40, loaded.getDurationMinutes());
        service.update(user, saved.getId(), new WorkoutRequest(date, null, lines, LocalTime.of(18, 30), null, null, null, null, null));
        loaded = service.requireOwned(user, saved.getId());
        assertEquals(LocalTime.of(18, 30), loaded.getStartTime()); assertNull(loaded.getDurationMinutes());
        service.update(user, saved.getId(), new WorkoutRequest(date, null, lines, null, null, null, null, null, null));
        assertNull(service.requireOwned(user, saved.getId()).getStartTime());
    }

    @Test void separateCardioPersistsAndReachesCoachWithoutChangingExerciseMetrics() throws Exception {
        var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); user = users.save(user);
        var exercise = exercises.create(new ExerciseRequest("Cardio timing " + UUID.randomUUID(), "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING));
        var lines = List.of(new WorkoutLineRequest(exercise.getId(), null, null, List.of(new WorkoutSegmentRequest(null, 30, BigDecimal.ZERO, null, null, null, null, null, null)), null));
        var date = LocalDate.of(2026, 8, 20);
        var request = new WorkoutRequest(date, null, lines, LocalTime.of(8, 0), 999, 2, 10, 0, 5);
        assertTrue(validator.validate(request).isEmpty());
        var saved = service.create(user, request);
        var loaded = service.requireOwned(user, saved.getId());
        assertEquals(17, loaded.getDurationMinutes());
        assertEquals(5, loaded.getCardioMinutes());
        assertEquals(5, WorkoutResponse.from(loaded).cardioMinutes());
        assertEquals(5, AssessmentWorkoutData.from(loaded).cardioMinutes());
        assertEquals(5, AssessmentWorkoutData.comparable(loaded, Set.of(exercise.getId())).cardioMinutes());
        var result = context.getHealthContext(user, date, date, Set.of(CoachDomain.TRAINING), OffsetDateTime.now());
        var training = (com.jllado.weightcontrol.api.dto.CoachDtos.TrainingContext) result.data().get(CoachDomain.TRAINING);
        assertEquals(5, training.days().getFirst().sessions().getFirst().cardioMinutes());
        assertEquals(30, metrics.summarizeWorkouts(service.findAll(user)).totalDurationSeconds());
        assertFalse(json.writeValueAsString(context.getReflectionContext(user, date)).contains("cardioMinutes"));
        assertFalse(validator.validate(new WorkoutRequest(date, null, lines, null, null, 0, 1, 0, -1)).isEmpty());
        final var owner = user;
        assertThrows(BadRequestException.class, () -> service.create(owner, new WorkoutRequest(date, null, lines, null, null, null, null, null, 5)));
        assertThrows(BadRequestException.class, () -> service.create(owner, new WorkoutRequest(date, null, lines, null, null, 1, Integer.MAX_VALUE, 0, 1)));
        assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class, () -> json.readValue("{\"cardioMinutes\":1.5}", WorkoutRequest.class));
    }

    @Test void multipleSessionsKeepIndependentIdentityAndAggregateWithoutLosingSameDayEntries() throws Exception {
        var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); user = users.save(user);
        var other = new User(); other.setEmail(UUID.randomUUID() + "@example.com"); other = users.save(other);
        var exercise = exercises.create(new ExerciseRequest("Session " + UUID.randomUUID(), "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING));
        var lines = List.of(new WorkoutLineRequest(exercise.getId(), null, null, List.of(new WorkoutSegmentRequest(null, 30, BigDecimal.ZERO, null, null, null, null, null, null)), null));
        var date = LocalDate.of(2026, 8, 20);
        var late = service.create(user, new WorkoutRequest(date, "Evening", lines, LocalTime.of(18, 0), 30, null, null, null, null));
        var untimed = service.create(user, new WorkoutRequest(date, "Untimed", lines, null, null, null, null, null, null));
        var morning = service.create(user, new WorkoutRequest(date, "Morning", lines, LocalTime.of(8, 0), 45, null, null, null, null));
        var sameTime = service.create(user, new WorkoutRequest(date, "Same time", lines, LocalTime.of(8, 0), 10, null, null, null, null));
        var expected = List.of(morning.getId(), sameTime.getId(), late.getId(), untimed.getId());
        assertEquals(expected, service.findAll(user).stream().map(Workout::getId).toList());
        assertEquals(expected, service.findPreloadWorkouts(user, date).stream().map(Workout::getId).toList());
        assertEquals(List.of(date), service.findDiaryPage(user, 0, 2).getContent());
        assertEquals(expected, service.findOnDates(user, List.of(date)).stream().map(Workout::getId).toList());
        assertTrue(service.findDiaryPage(user, 1, 2).isEmpty());
        assertEquals(4, service.findDashboardWorkouts(user, date).currentWorkouts().size());
        assertEquals(4, service.findDashboardWorkouts(user, date.plusWeeks(1)).previousWeekWorkouts().size());
        assertTrue(service.findAll(other).isEmpty());
        final var owner = other;
        assertThrows(NotFoundException.class, () -> service.requireOwned(owner, morning.getId()));
        var result = context.getHealthContext(user, date, date, Set.of(CoachDomain.TRAINING), OffsetDateTime.now());
        var training = (com.jllado.weightcontrol.api.dto.CoachDtos.TrainingContext) result.data().get(CoachDomain.TRAINING);
        assertEquals(1, training.days().size());
        assertEquals(4, training.days().stream().flatMap(day -> day.sessions().stream()).map(session -> session.sessionReference()).distinct().count());
        var reflection = context.getReflectionContext(user, date);
        assertEquals(4, reflection.workouts().days().size());
        assertFalse(json.writeValueAsString(reflection).contains("sessionReference"));
        assertThrows(NotFoundException.class, () -> assessments.getContext(owner, date, null));
        assertEquals(120, metrics.summarizeWorkouts(service.findAll(user)).totalDurationSeconds());
        assertEquals(4, metrics.summarizeWorkouts(service.findAll(user)).workoutCount());
        String reference = morning.getSessionReference();
        service.update(user, morning.getId(), new WorkoutRequest(date.plusDays(1), "Moved", lines, null, null, null, null, null, null));
        assertEquals(reference, service.requireOwned(user, morning.getId()).getSessionReference());
        service.delete(user, late.getId());
        final var sessionOwner = user;
        assertThrows(BadRequestException.class, () -> assessments.getContext(sessionOwner, date, late.getSessionReference()));
        assertEquals(2, service.findDashboardWorkouts(user, date).currentWorkouts().size());
        assertEquals(10, service.requireOwned(user, sameTime.getId()).getDurationMinutes());
    }

    @Test void dailyRatingsInvalidateOnAddEditMoveAndFinalDeletion() {
        final var user = assessmentOwner();
        var date = LocalDate.of(2026, 8, 20);
        var request = assessmentWorkout(date);
        var first = service.create(user, request);
        saveDailyRating(user, date);
        assertTrue(ratings.findByUserAndWorkoutDate(user, date).isPresent());
        var second = service.create(user, request);
        assertTrue(ratings.findByUserAndWorkoutDate(user, date).isEmpty());
        saveDailyRating(user, date);
        service.update(user, first.getId(), request);
        assertTrue(ratings.findByUserAndWorkoutDate(user, date).isEmpty());
        var tomorrow = new WorkoutRequest(date.plusDays(1), null, request.lines(), null, null, null, null, null, null);
        var third = service.create(user, tomorrow);
        saveDailyRating(user, date); saveDailyRating(user, date.plusDays(1));
        service.update(user, first.getId(), tomorrow);
        assertTrue(ratings.findByUserAndWorkoutDate(user, date).isEmpty());
        assertTrue(ratings.findByUserAndWorkoutDate(user, date.plusDays(1)).isEmpty());
        saveDailyRating(user, date);
        service.delete(user, second.getId());
        assertTrue(ratings.findByUserAndWorkoutDate(user, date).isEmpty());
        assertThrows(NotFoundException.class, () -> assessments.getContext(user, date, null));
        assertEquals(2, service.findOnDates(user, List.of(date.plusDays(1))).size());
        assertTrue(service.requireOwned(user, third.getId()).getLines().size() > 0);
    }

    @Test void concurrentMutationRejectsWaitingStaleRatingAndWaitingMutationClearsSavedRating() throws Exception {
        final var user = assessmentOwner();
        var date = LocalDate.of(2026, 8, 20);
        var request = assessmentWorkout(date);
        var workout = service.create(user, request);
        var stale = dailyRatingRequest(user, date);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var pending = transaction.execute(status -> {
                users.findByIdForUpdate(user.getId()).orElseThrow();
                var started = new java.util.concurrent.CountDownLatch(1);
                var save = executor.submit(() -> { started.countDown(); return assessments.save(user, date, null, stale); });
                try {
                    assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    assertThrows(java.util.concurrent.TimeoutException.class, () -> save.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
                } catch (InterruptedException error) { throw new RuntimeException(error); }
                service.create(user, request);
                return save;
            });
            var rejected = assertThrows(java.util.concurrent.ExecutionException.class, () -> pending.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(BadRequestException.class, rejected.getCause());
            assertTrue(ratings.findByUserAndWorkoutDate(user, date).isEmpty());
            var current = dailyRatingRequest(user, date);
            var pendingEdit = transaction.execute(status -> {
                assessments.save(user, date, null, current);
                var started = new java.util.concurrent.CountDownLatch(1);
                var edit = executor.submit(() -> { started.countDown(); return service.update(user, workout.getId(), request); });
                try {
                    assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    assertThrows(java.util.concurrent.TimeoutException.class, () -> edit.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
                } catch (InterruptedException error) { throw new RuntimeException(error); }
                return edit;
            });
            pendingEdit.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(ratings.findByUserAndWorkoutDate(user, date).isEmpty());
        }
    }

    private User assessmentOwner() {
        var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); user = users.save(user);
        var plan = new CoachingPlan(); plan.setUser(user); plan.setGoal("Consistent training"); plan.setStartDate(LocalDate.of(2026, 8, 1));
        plan.setPrinciples(List.of("Consistency")); plan.setPriorities(List.of("Strength")); plan.setActions(List.of("Train regularly")); plans.saveAndFlush(plan);
        return user;
    }

    private WorkoutRequest assessmentWorkout(LocalDate date) {
        var exercise = exercises.create(new ExerciseRequest("Daily " + UUID.randomUUID(), "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING));
        return new WorkoutRequest(date, null, List.of(new WorkoutLineRequest(exercise.getId(), null, null, List.of(new WorkoutSegmentRequest(null, 30, BigDecimal.ZERO, null, null, null, null, null, null)), null)), null, 10, null, null, null, null);
    }

    private com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.SaveWorkoutAssessmentRequest dailyRatingRequest(User user, LocalDate date) {
        var context = assessments.getContext(user, date, null);
        return new com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.SaveWorkoutAssessmentRequest(8, 7, "Consistent training.", "Good technique.", "Add variety.", "Repeat tomorrow.", context.planUpdatedAt(), context.workoutContextToken(), true);
    }

    private void saveDailyRating(User user, LocalDate date) { assessments.save(user, date, null, dailyRatingRequest(user, date)); }

    @Test void diaryAndPreloadHydrateOnlySelectedSessions() {
        var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); user = users.save(user);
        var other = new User(); other.setEmail(UUID.randomUUID() + "@example.com"); other = users.save(other);
        var exercise = exercises.create(new ExerciseRequest("Pagination " + UUID.randomUUID(), "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING));
        var segment = new WorkoutSegmentRequest(null, 30, BigDecimal.ZERO, null, null, null, null, null, null);
        var lines = List.of(new WorkoutLineRequest(exercise.getId(), null, null, List.of(segment, segment), null));
        var date = LocalDate.of(2026, 8, 20);
        var ids = new ArrayList<Long>();
        for (int i = 0; i < 45; i++) {
            ids.add(service.create(user, new WorkoutRequest(date.minusDays(i / 3), null, lines, LocalTime.of(8 + i % 3, 0), null, null, null, null, null)).getId());
        }
        service.create(other, new WorkoutRequest(date.plusDays(1), null, lines, null, null, null, null, null, null));
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            var page = service.findDiaryPage(user, 1, 10);
            assertEquals(15, page.getTotalElements());
            assertEquals(0, statistics.getEntityStatistics(Workout.class.getName()).getLoadCount());
            var sessions = service.findOnDates(user, page.getContent());
            assertEquals(ids.subList(30, 45), sessions.stream().map(Workout::getId).toList());
            assertEquals(15, statistics.getEntityStatistics(Workout.class.getName()).getLoadCount());
            assertTrue(sessions.stream().allMatch(workout -> WorkoutResponse.from(workout).lines().getFirst().sets().size() == 2));
            statistics.clear();
            var preloads = service.findPreloadWorkouts(user, date.minusDays(1));
            assertEquals(ids.subList(3, 43), preloads.stream().map(Workout::getId).toList());
            assertEquals(40, statistics.getEntityStatistics(Workout.class.getName()).getLoadCount());
            assertEquals(2, WorkoutResponse.from(preloads.getLast()).lines().getFirst().sets().size());
            statistics.clear();
            var empty = service.findDiaryPage(user, 5, 10);
            assertTrue(empty.isEmpty());
            assertEquals(15, empty.getTotalElements());
            assertTrue(service.findPreloadWorkouts(user, date.minusDays(20)).isEmpty());
            assertEquals(0, statistics.getEntityStatistics(Workout.class.getName()).getLoadCount());
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test void validatesTimingAtTheRequestAndServiceBoundaries() throws Exception {
        var owner = new User(); owner.setEmail(UUID.randomUUID() + "@example.com"); final var user = users.save(owner);
        var date = LocalDate.of(2026, 8, 20);
        for (var invalid : List.of(
            new WorkoutRequest(date, null, List.of(), null, 0, null, null, null, null),
            new WorkoutRequest(date, null, List.of(), null, null, -1, 2, 3, null))) {
            assertTrue(validator.validate(invalid).stream().anyMatch(v -> v.getPropertyPath().toString().endsWith("Minutes")));
        }
        for (var invalid : List.of(
            new WorkoutRequest(date, null, List.of(), null, null, 5, null, 2, null),
            new WorkoutRequest(date, null, List.of(), null, null, 0, 0, 0, null),
            new WorkoutRequest(date, null, List.of(), null, null, Integer.MAX_VALUE, 1, 0, null))) {
            assertThrows(BadRequestException.class, () -> service.create(user, invalid));
        }
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> json.readValue("{\"startTime\":\"25:00\"}", WorkoutRequest.class));
        for (String field : List.of("durationMinutes", "warmUpMinutes", "trainingMinutes", "stretchingMinutes")) {
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> json.readValue("{\"" + field + "\":1.5}", WorkoutRequest.class));
        }
        var request = json.readValue("{\"startTime\":\"18:30\"}", WorkoutRequest.class);
        assertEquals(LocalTime.of(18, 30), request.startTime());
    }
}
