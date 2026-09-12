package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StretchingBreathsMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("stretching_breaths");
    @Test void preservesTimedWorkoutAndTemplateHolds() throws Exception {
        flyway("72").migrate();
        try (var connection = DATABASE.createConnection(""); var sql = connection.createStatement()) {
            sql.executeUpdate("""
                INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                VALUES ('breaths@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                """);
            sql.executeUpdate("INSERT INTO exercises (id, name, description, tracking_mode, exercise_type) VALUES (9000, 'Migration stretch', 'Hold', 'SECONDS', 'STRETCHING')");
            sql.executeUpdate("INSERT INTO workouts (user_id, workout_date, session_reference) VALUES (1, '2026-09-01', UUID())");
            sql.executeUpdate("INSERT INTO workout_lines (workout_id, exercise_id, position) VALUES (1, 9000, 0)");
            sql.executeUpdate("INSERT INTO workout_segments (workout_line_id, position, duration_seconds) VALUES (1, 0, 30), (1, 1, 65)");
            sql.executeUpdate("INSERT INTO stretching_sets (user_id, name, normalized_name) VALUES (1, 'Morning', 'morning')");
            sql.executeUpdate("INSERT INTO stretching_set_entries (stretching_set_id, exercise_id, position) VALUES (1, 9000, 0)");
            sql.executeUpdate("INSERT INTO stretching_set_holds (entry_id, position, duration_seconds) VALUES (1, 0, 30), (1, 1, 65)");
        }
        flyway("73").migrate();
        try (var connection = DATABASE.createConnection(""); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT l.stretching_unit, s.duration_seconds, s.breaths FROM workout_lines l JOIN workout_segments s ON s.workout_line_id = l.id ORDER BY s.position")) {
                for (int seconds : new int[]{30, 65}) { assertTrue(rows.next()); assertEquals("SECONDS", rows.getString(1)); assertEquals(seconds, rows.getInt(2)); assertNull(rows.getObject(3)); }
                assertFalse(rows.next());
            }
            try (var rows = sql.executeQuery("SELECT e.stretching_unit, h.duration_seconds FROM stretching_set_entries e JOIN stretching_set_holds h ON h.entry_id = e.id ORDER BY h.position")) {
                for (int seconds : new int[]{30, 65}) { assertTrue(rows.next()); assertEquals("SECONDS", rows.getString(1)); assertEquals(seconds, rows.getInt(2)); }
                assertFalse(rows.next());
            }
            sql.executeUpdate("INSERT INTO stretching_set_breaths (entry_id, position, breaths) VALUES (1, 0, 5), (1, 1, 8)");
            try (var rows = sql.executeQuery("SELECT breaths FROM stretching_set_breaths ORDER BY position")) { assertTrue(rows.next()); assertEquals(5, rows.getInt(1)); assertTrue(rows.next()); assertEquals(8, rows.getInt(1)); }
        }
    }
    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
