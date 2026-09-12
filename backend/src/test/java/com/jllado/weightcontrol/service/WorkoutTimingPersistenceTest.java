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
