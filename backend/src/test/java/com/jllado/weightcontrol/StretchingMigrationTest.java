package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StretchingMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("stretching_migration");

    @Test
    void seedsTimedStretchesWithoutChangingExistingCatalogOrWorkouts() throws Exception {
        flyway("61").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO exercises (name, description, tracking_mode) VALUES ('WALL CALF STRETCH', 'Keep my description', 'REPS')");
            statement.executeUpdate("INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) VALUES ('stretching@example.com', 2000, 2000, 2000, 2000, 2000, 2000, 2000)");
            statement.executeUpdate("INSERT INTO workouts (id, user_id, workout_date, note) SELECT 1, id, '2026-09-01', 'Keep my workout' FROM users WHERE email = 'stretching@example.com'");
            statement.executeUpdate("INSERT INTO workout_lines (id, workout_id, exercise_id, position) SELECT 1, 1, id, 0 FROM exercises WHERE name = 'WALL CALF STRETCH'");
            statement.executeUpdate("INSERT INTO workout_segments (workout_line_id, position, repetitions) VALUES (1, 0, 8)");
        }
        flyway("62").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT count(*), min(tracking_mode), max(tracking_mode), sum(default_warm_up), count(default_repetitions) FROM exercises WHERE exercise_type = 'STRETCHING'")) {
                assertTrue(result.next()); assertEquals(7, result.getInt(1)); assertEquals("SECONDS", result.getString(2)); assertEquals("SECONDS", result.getString(3)); assertEquals(0, result.getInt(4)); assertEquals(0, result.getInt(5));
            }
            try (var result = statement.executeQuery("SELECT description, exercise_type, tracking_mode FROM exercises WHERE name = 'Wall calf stretch'")) {
                assertTrue(result.next()); assertEquals("Keep my description", result.getString(1)); assertEquals("TRAINING", result.getString(2)); assertEquals("REPS", result.getString(3)); assertFalse(result.next());
            }
            try (var result = statement.executeQuery("SELECT note, repetitions, workout_lines.position FROM workouts JOIN workout_lines ON workout_id = workouts.id JOIN workout_segments ON workout_line_id = workout_lines.id")) {
                assertTrue(result.next()); assertEquals("Keep my workout", result.getString(1)); assertEquals(8, result.getInt(2)); assertEquals(0, result.getInt(3)); assertFalse(result.next());
            }
            statement.executeUpdate("INSERT INTO workout_lines (id, workout_id, exercise_id, position) SELECT 2, 1, id, 1 FROM exercises WHERE name = 'Wall hamstring stretch'");
            statement.executeUpdate("INSERT INTO workout_segments (workout_line_id, position, duration_seconds) VALUES (2, 0, 30), (2, 1, 30)");
            try (var result = statement.executeQuery("SELECT sum(duration_seconds), count(weight) FROM workout_segments WHERE workout_line_id = 2")) {
                assertTrue(result.next()); assertEquals(60, result.getInt(1)); assertEquals(0, result.getInt(2));
            }
        }
        assertEquals(0, flyway("62").migrate().migrationsExecuted);
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
