package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-30-bytes-long"})
class TrainingBalancePersistenceTest {
    @TestConfiguration(proxyBeanMethods = false) static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("training_balance"); }
    }
    @Autowired TrainingBalanceService balance;
    @Autowired WorkoutService workouts;
    @Autowired ExerciseService exercises;
    @Autowired UserRepository users;

    @Test void queriesTheWholeHistoricalWeekAcrossSessionsAndRefreshesCurrentClassification() {
        var owner = user(); var other = user(); var date = LocalDate.of(2025, 12, 31);
        var press = exercises.create(new ExerciseRequest("Balance press " + UUID.randomUUID(), "Press", ExerciseTrackingMode.REPS, ExerciseType.TRAINING, PrimaryMuscleGroup.CHEST));
        var hold = exercises.create(new ExerciseRequest("Balance hold " + UUID.randomUUID(), "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.TRAINING, PrimaryMuscleGroup.TRICEPS));
        var start = LocalDate.of(2025, 12, 27); var end = LocalDate.of(2026, 1, 2);
        var saved = workouts.create(owner, request(start, press, 2));
        workouts.create(owner, request(start, press, 3));
        workouts.create(owner, request(end, hold, 4));
        workouts.create(owner, request(start.minusDays(1), press, 5));
        workouts.create(owner, request(end.plusDays(1), press, 6));
        workouts.create(other, request(date, press, 7));
        for (int i = 1; i <= 12; i++) workouts.create(owner, request(end.plusDays(i + 1), press, 1));
        var result = balance.week(owner, date);
        assertEquals(start, result.weekStart()); assertEquals(end, result.weekEnd()); assertEquals(9, result.totalSets());
        assertEquals(5, result.groups().get(0).sets()); assertEquals(4, result.groups().get(4).sets());
        exercises.update(press.getId(), new ExerciseRequest(press.getName(), "Press", ExerciseTrackingMode.REPS, ExerciseType.TRAINING, PrimaryMuscleGroup.SHOULDERS));
        result = balance.week(owner, date);
        assertEquals(0, result.groups().get(0).sets()); assertEquals(5, result.groups().get(2).sets());
        workouts.update(owner, saved.getId(), request(start, press, 1));
        assertEquals(8, balance.week(owner, date).totalSets());
        workouts.delete(owner, saved.getId());
        assertEquals(7, balance.week(owner, date).totalSets());
        assertEquals(7, balance.week(other, date).totalSets());
    }
    private User user() { var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); return users.save(user); }
    private WorkoutRequest request(LocalDate date, Exercise exercise, int count) {
        var segments = new ArrayList<WorkoutSegmentRequest>();
        for (int i = 0; i < count; i++) segments.add(new WorkoutSegmentRequest(exercise.getTrackingMode() == ExerciseTrackingMode.REPS ? 10 : null, exercise.getTrackingMode() == ExerciseTrackingMode.SECONDS ? 30 : null, i == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(i * 7.5), null, null, null, null, null, null));
        return new WorkoutRequest(date, null, List.of(new WorkoutLineRequest(exercise.getId(), null, null, segments, null)), null, null, null, null, null, null);
    }
}
