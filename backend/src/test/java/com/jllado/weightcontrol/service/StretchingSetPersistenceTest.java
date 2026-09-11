package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-30-bytes-long"})
class StretchingSetPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("stretching"); }
    }
    @Autowired StretchingSetService service;
    @Autowired ExerciseService exercises;
    @Autowired WorkoutService workouts;
    @Autowired UserRepository users;
    @Autowired Validator validator;
    @Autowired JdbcTemplate jdbc;

    @Test void persistsOrderedHoldsScopesOwnersAndPreservesRecordedWorkouts() {
        User owner = user("stretch-owner"), other = user("stretch-other");
        var first = exercises.create(new ExerciseRequest("Test stretch one", "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING));
        var second = exercises.create(new ExerciseRequest("Test stretch two", "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING));
        var a = new StretchingSetEntryRequest(first.getId(), List.of(30, 65));
        var b = new StretchingSetEntryRequest(second.getId(), List.of(20));
        var set = service.create(owner, new StretchingSetRequest(" Morning ", List.of(a, b)));
        assertEquals("Morning", set.name());
        assertEquals(List.of(a, b), service.findAll(owner).getFirst().entries());
        assertTrue(service.findAll(other).isEmpty());
        assertThrows(NotFoundException.class, () -> service.update(other, set.id(), new StretchingSetRequest("Stolen", List.of(a))));
        assertThrows(NotFoundException.class, () -> service.delete(other, set.id()));
        assertThrows(BadRequestException.class, () -> service.create(owner, new StretchingSetRequest("MORNING", List.of(a))));
        var otherSet = service.create(other, new StretchingSetRequest("Morning", List.of(a)));
        assertThrows(BadRequestException.class, () -> exercises.delete(first.getId()));
        assertThrows(BadRequestException.class, () -> exercises.update(first.getId(), new ExerciseRequest("Test stretch one", "Hold", ExerciseTrackingMode.REPS, ExerciseType.TRAINING)));
        var recorded = workouts.create(owner, new WorkoutRequest(java.time.LocalDate.of(2026, 9, 1), null, List.of(new WorkoutLineRequest(first.getId(), null, null, List.of(new WorkoutSegmentRequest(null, 30, null, null, null, null, null, null))))));
        service.update(owner, set.id(), new StretchingSetRequest("Evening", List.of(b, new StretchingSetEntryRequest(first.getId(), List.of(90)))));
        assertEquals(List.of(b, new StretchingSetEntryRequest(first.getId(), List.of(90))), service.findAll(owner).getFirst().entries());
        service.delete(owner, set.id());
        service.delete(other, otherSet.id());
        assertEquals(0, jdbc.queryForObject("select count(*) from stretching_set_holds", Integer.class));
        assertEquals(30, workouts.findAll(owner).getFirst().getLines().getFirst().getSegments().getFirst().getDurationSeconds());
        assertNotNull(recorded);
        exercises.delete(second.getId());
    }

    @Test void rejectsInvalidTemplates() {
        var owner = user("stretch-validation");
        var stretch = exercises.create(new ExerciseRequest("Validation stretch", "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING));
        assertThrows(BadRequestException.class, () -> service.create(owner, new StretchingSetRequest("Invalid step", List.of(new StretchingSetEntryRequest(stretch.getId(), List.of(32))))));
        var training = exercises.create(new ExerciseRequest("Validation training", "Train", ExerciseTrackingMode.REPS, ExerciseType.TRAINING));
        var entry = new StretchingSetEntryRequest(stretch.getId(), List.of(30));
        assertThrows(BadRequestException.class, () -> service.create(owner, new StretchingSetRequest("Duplicate", List.of(entry, entry))));
        assertThrows(BadRequestException.class, () -> service.create(owner, new StretchingSetRequest("Training", List.of(new StretchingSetEntryRequest(training.getId(), List.of(30))))));
        assertThrows(NotFoundException.class, () -> service.create(owner, new StretchingSetRequest("Missing", List.of(new StretchingSetEntryRequest(Long.MAX_VALUE, List.of(30))))));
        for (var request : List.of(new StretchingSetRequest(" ", List.of(entry)), new StretchingSetRequest("x".repeat(256), List.of(entry)), new StretchingSetRequest("Empty", List.of()), new StretchingSetRequest("Zero", List.of(new StretchingSetEntryRequest(stretch.getId(), List.of(0)))), new StretchingSetRequest("No holds", List.of(new StretchingSetEntryRequest(stretch.getId(), List.of()))))) {
            assertFalse(validator.validate(request).isEmpty());
        }
        assertTrue(service.findAll(owner).isEmpty());
    }
    private User user(String name) { var user = new User(); user.setEmail(name + "@example.com"); return users.save(user); }
}
