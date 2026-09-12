package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.repository.ExerciseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {
    "app.auth.google-client-id=test-client-id",
    "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"
})
class MariaDbSchemaValidationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        @ServiceConnection
        MariaDBContainer<?> database() {
            return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("weight_control");
        }
    }

    @Autowired private ExerciseRepository exerciseRepository;

    @Test
    void migrationsMatchTheHibernateSchema() {
        var stretching = exerciseRepository.findAllByOrderByNameAsc().stream().filter(exercise -> exercise.getExerciseType() == ExerciseType.STRETCHING).toList();
        assertEquals(34, stretching.size());
        assertTrue(stretching.stream().allMatch(exercise -> exercise.getTrackingMode() == ExerciseTrackingMode.SECONDS));
        assertTrue(stretching.stream().allMatch(exercise -> !exercise.getDescription().isBlank() && exercise.getDescription().length() <= 500));
    }
}
