package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RemoveDefaultWarmUpsMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("remove_defaults");

    @Test void removesDefaultsAndPreservesRecordedWarmUps() throws Exception {
        flyway("66").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id, email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) VALUES (1, 'warmup@example.test', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("INSERT INTO workouts (id, user_id, workout_date) VALUES (1, 1, '2026-09-11')");
            statement.executeUpdate("INSERT INTO workout_lines (id, workout_id, exercise_id, position) SELECT 1, 1, id, 0 FROM exercises WHERE name = 'McGill Big Three'");
            statement.executeUpdate("INSERT INTO workout_segments (workout_line_id, position, repetitions) VALUES (1, 0, 6)");
        }
        flyway("67").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT e.name, e.exercise_type, s.repetitions FROM exercises e JOIN workout_lines l ON l.exercise_id = e.id JOIN workout_segments s ON s.workout_line_id = l.id WHERE l.workout_id = 1")) {
                assertTrue(rows.next()); assertEquals("McGill Big Three", rows.getString(1)); assertEquals("WARM_UP", rows.getString(2)); assertEquals(6, rows.getInt(3)); assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'exercises' AND column_name IN ('default_warm_up', 'default_repetitions')")) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            }
        }
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
