package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WorkoutSessionsMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("workout_sessions");
    @Test void preservesHistoryAndBackfillsUniqueReferencesWhileAllowingRepeatedDates() throws Exception {
        flyway("70").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                VALUES ('sessions@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                """);
            statement.executeUpdate("INSERT INTO workouts (user_id, workout_date, note, start_time, duration_minutes) VALUES (1, '2026-08-20', 'Morning', '08:00', 45), (1, '2026-08-21', 'Untimed', null, null)");
        }
        flyway("71").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            String reference;
            try (var rows = statement.executeQuery("SELECT * FROM workouts ORDER BY id")) {
                assertTrue(rows.next()); reference = rows.getString("session_reference");
                assertNotNull(java.util.UUID.fromString(reference));
                assertEquals("Morning", rows.getString("note")); assertEquals(45, rows.getInt("duration_minutes"));
                assertEquals("08:00:00", rows.getString("start_time"));
                assertTrue(rows.next()); assertNotEquals(reference, rows.getString("session_reference"));
                assertNull(rows.getObject("duration_minutes")); assertNull(rows.getObject("start_time"));
            }
            statement.executeUpdate("INSERT INTO workouts (user_id, workout_date, session_reference, start_time) VALUES (1, '2026-08-20', UUID(), '08:00'), (1, '2026-08-20', UUID(), null)");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM workouts WHERE workout_date = '2026-08-20'")) { assertTrue(rows.next()); assertEquals(3, rows.getInt(1)); }
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("INSERT INTO workouts (user_id, workout_date, session_reference) VALUES (1, '2026-08-22', '" + reference + "')"));
        }
    }
    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
