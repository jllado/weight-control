package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseResponse;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.repository.ExerciseRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-30-bytes-long"})
class ExerciseEstimatePersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("exercise_estimate_persistence"); }
    }
    @Autowired ExerciseService service;
    @Autowired ExerciseRepository repository;

    @Test void createsUpdatesAndClearsEstimateWithoutChangingOtherMetadata() {
        var name = "Estimate " + UUID.randomUUID();
        var saved = service.create(request(name, 60));
        assertEquals(60, ExerciseResponse.from(repository.findById(saved.getId()).orElseThrow()).estimatedSecondsPerSet());
        service.update(saved.getId(), request(name, 45));
        assertEquals(45, repository.findById(saved.getId()).orElseThrow().getEstimatedSecondsPerSet());
        service.update(saved.getId(), request(name, null));
        var loaded = repository.findById(saved.getId()).orElseThrow();
        assertNull(loaded.getEstimatedSecondsPerSet()); assertEquals(name, loaded.getName()); assertEquals("Move", loaded.getDescription());
        assertEquals(ExerciseType.WARM_UP, loaded.getExerciseType()); assertEquals(ExerciseTrackingMode.SECONDS, loaded.getTrackingMode());
    }

    private ExerciseRequest request(String name, Integer estimate) {
        return new ExerciseRequest(name, "Move", ExerciseTrackingMode.SECONDS, ExerciseType.WARM_UP, null, null, estimate);
    }
}
