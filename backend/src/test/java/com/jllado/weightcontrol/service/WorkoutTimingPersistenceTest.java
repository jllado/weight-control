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

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-30-bytes-long"})
class WorkoutTimingPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("workout_timing"); }
    }
    @Autowired WorkoutService service;
    @Autowired ExerciseService exercises;
    @Autowired UserRepository users;
    @Autowired Validator validator;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired HealthDataContextService context;
    @Autowired WeeklyMetricsCalculator metrics;
    @Autowired WorkoutAssessmentService assessments;

    @Test void persistsCalculatesAndClearsSessionTimingWithoutChangingExerciseDurations() throws Exception {
        var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); user = users.save(user);
        var exercise = exercises.create(new ExerciseRequest("Timing " + UUID.randomUUID(), "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING));
        var lines = List.of(new WorkoutLineRequest(exercise.getId(), null, null, List.of(new WorkoutSegmentRequest(null, 30, BigDecimal.ZERO, null, null, null, null, null))));
        var date = LocalDate.of(2026, 8, 20);
        var legacy = json.readValue("{\"workoutDate\":\"2026-08-20\",\"lines\":[]}", WorkoutRequest.class);
        assertNull(legacy.startTime()); assertNull(legacy.durationMinutes());
        var saved = service.create(user, new WorkoutRequest(date, null, lines, null, null, null, null, null));
        assertNull(service.requireOwned(user, saved.getId()).getDurationMinutes());
        assertNull(jdbc.queryForObject("select start_time from workouts where id = ?", String.class, saved.getId()));

        service.update(user, saved.getId(), new WorkoutRequest(date, null, lines, LocalTime.MIDNIGHT, 1, 10, 45, 0));
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

        service.update(user, saved.getId(), new WorkoutRequest(date, null, lines, null, 40, null, null, null));
        loaded = service.requireOwned(user, saved.getId());
        assertNull(loaded.getStartTime()); assertNull(loaded.getWarmUpMinutes()); assertEquals(40, loaded.getDurationMinutes());
        service.update(user, saved.getId(), new WorkoutRequest(date, null, lines, LocalTime.of(18, 30), null, null, null, null));
        loaded = service.requireOwned(user, saved.getId());
        assertEquals(LocalTime.of(18, 30), loaded.getStartTime()); assertNull(loaded.getDurationMinutes());
        service.update(user, saved.getId(), new WorkoutRequest(date, null, lines, null, null, null, null, null));
        assertNull(service.requireOwned(user, saved.getId()).getStartTime());
    }

    @Test void multipleSessionsKeepIndependentIdentityAndAggregateWithoutLosingSameDayEntries() throws Exception {
        var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); user = users.save(user);
        var other = new User(); other.setEmail(UUID.randomUUID() + "@example.com"); other = users.save(other);
        var exercise = exercises.create(new ExerciseRequest("Session " + UUID.randomUUID(), "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING));
        var lines = List.of(new WorkoutLineRequest(exercise.getId(), null, null, List.of(new WorkoutSegmentRequest(null, 30, BigDecimal.ZERO, null, null, null, null, null))));
        var date = LocalDate.of(2026, 8, 20);
        var late = service.create(user, new WorkoutRequest(date, "Evening", lines, LocalTime.of(18, 0), 30, null, null, null));
        var untimed = service.create(user, new WorkoutRequest(date, "Untimed", lines, null, null, null, null, null));
        var morning = service.create(user, new WorkoutRequest(date, "Morning", lines, LocalTime.of(8, 0), 45, null, null, null));
        var sameTime = service.create(user, new WorkoutRequest(date, "Same time", lines, LocalTime.of(8, 0), 10, null, null, null));
        var expected = List.of(morning.getId(), sameTime.getId(), late.getId(), untimed.getId());
        assertEquals(expected, service.findAll(user).stream().map(Workout::getId).toList());
        assertEquals(expected, service.findPreloadWorkouts(user, date).stream().map(Workout::getId).toList());
        assertEquals(expected.subList(0, 2), service.findDiaryPage(user, 0, 2).stream().map(Workout::getId).toList());
        assertEquals(expected.subList(2, 4), service.findDiaryPage(user, 1, 2).stream().map(Workout::getId).toList());
        assertEquals(4, service.findDashboardWorkouts(user, date).currentWorkouts().size());
        assertEquals(4, service.findDashboardWorkouts(user, date.plusWeeks(1)).previousWeekWorkouts().size());
        assertTrue(service.findAll(other).isEmpty());
        final var owner = other;
        assertThrows(NotFoundException.class, () -> service.requireOwned(owner, morning.getId()));
        var result = context.getHealthContext(user, date, date, Set.of(CoachDomain.TRAINING), OffsetDateTime.now());
        var training = (com.jllado.weightcontrol.api.dto.CoachDtos.TrainingContext) result.data().get(CoachDomain.TRAINING);
        assertEquals(4, training.days().size());
        assertEquals(4, training.days().stream().map(day -> day.sessionReference()).distinct().count());
        var reflection = context.getReflectionContext(user, date);
        assertEquals(4, reflection.workouts().days().size());
        assertFalse(json.writeValueAsString(reflection).contains("sessionReference"));
        assertThrows(NotFoundException.class, () -> assessments.getContext(owner, date, morning.getSessionReference()));
        assertEquals(120, metrics.summarizeWorkouts(service.findAll(user)).totalDurationSeconds());
        assertEquals(4, metrics.summarizeWorkouts(service.findAll(user)).workoutCount());
        String reference = morning.getSessionReference();
        service.update(user, morning.getId(), new WorkoutRequest(date.plusDays(1), "Moved", lines, null, null, null, null, null));
        assertEquals(reference, service.requireOwned(user, morning.getId()).getSessionReference());
        service.delete(user, late.getId());
        final var sessionOwner = user;
        assertThrows(NotFoundException.class, () -> assessments.getContext(sessionOwner, date, late.getSessionReference()));
        assertEquals(2, service.findDashboardWorkouts(user, date).currentWorkouts().size());
        assertEquals(10, service.requireOwned(user, sameTime.getId()).getDurationMinutes());
    }

    @Test void validatesTimingAtTheRequestAndServiceBoundaries() throws Exception {
        var date = LocalDate.of(2026, 8, 20);
        for (var invalid : List.of(
            new WorkoutRequest(date, null, List.of(), null, 0, null, null, null),
            new WorkoutRequest(date, null, List.of(), null, null, -1, 2, 3))) {
            assertTrue(validator.validate(invalid).stream().anyMatch(v -> v.getPropertyPath().toString().endsWith("Minutes")));
        }
        for (var invalid : List.of(
            new WorkoutRequest(date, null, List.of(), null, null, 5, null, 2),
            new WorkoutRequest(date, null, List.of(), null, null, 0, 0, 0),
            new WorkoutRequest(date, null, List.of(), null, null, Integer.MAX_VALUE, 1, 0))) {
            assertThrows(BadRequestException.class, () -> service.create(new User(), invalid));
        }
        assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> json.readValue("{\"startTime\":\"25:00\"}", WorkoutRequest.class));
        for (String field : List.of("durationMinutes", "warmUpMinutes", "trainingMinutes", "stretchingMinutes")) {
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class, () -> json.readValue("{\"" + field + "\":1.5}", WorkoutRequest.class));
        }
        var request = json.readValue("{\"startTime\":\"18:30\"}", WorkoutRequest.class);
        assertEquals(LocalTime.of(18, 30), request.startTime());
    }
}
