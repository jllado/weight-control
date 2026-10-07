package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class GuidedWorkoutFieldsMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("guided_workout_fields");

    @Test void addsGuidedFieldsWithoutChangingExistingWorkoutValues() throws Exception {
        flyway("100").migrate();
        try (Connection connection = DATABASE.createConnection(""); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                VALUES ('guided-fields@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                """);
            statement.executeUpdate("INSERT INTO workouts (user_id, workout_date, session_reference) VALUES (1, '2040-01-01', UUID())");
            statement.executeUpdate("INSERT INTO workout_lines (workout_id, exercise_id, position) SELECT 1, id, 0 FROM exercises WHERE name = 'Squat'");
            statement.executeUpdate("INSERT INTO workout_segments (workout_line_id, position, repetitions, weight) VALUES (1, 0, 10, 20)");
        }

        flyway("101").migrate();

        try (Connection connection = DATABASE.createConnection(""); Statement statement = connection.createStatement();
             var rows = statement.executeQuery("""
                 SELECT workout_lines.exercise_duration_seconds, workout_segments.skipped, workout_segments.repetitions, workout_segments.weight
                 FROM workout_lines JOIN workout_segments ON workout_segments.workout_line_id = workout_lines.id
                 WHERE workout_lines.workout_id = 1
                 """)) {
            assertTrue(rows.next());
            assertNull(rows.getObject("exercise_duration_seconds"));
            assertFalse(rows.getBoolean("skipped"));
            assertEquals(10, rows.getInt("repetitions"));
            assertEquals(20, rows.getBigDecimal("weight").intValueExact());
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").target(target).load();
    }
}
