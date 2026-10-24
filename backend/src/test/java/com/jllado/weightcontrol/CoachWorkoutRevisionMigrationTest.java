package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CoachWorkoutRevisionMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_coach_workout_revision");

    @Test
    void existingWorkoutReceivesZeroRevisionWithoutChangingItsSnapshot() throws Exception {
        flyway("110").migrate();
        Timestamp beforeUpdatedAt;
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('coach-workout-migration@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into workouts (user_id, workout_date, session_reference, note, sauna_session, sauna_rounds_json, duration_minutes) select id, '2026-08-20', 'legacy-workout-486', 'Recorded sauna', true, '[12,8]', 20 from users where email = 'coach-workout-migration@example.com'");
            try (var row = statement.executeQuery("select updated_at from workouts where session_reference = 'legacy-workout-486'")) {
                row.next();
                beforeUpdatedAt = row.getTimestamp(1);
            }
        }

        flyway("111").migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement();
             var row = statement.executeQuery("select workout_date, note, sauna_session, sauna_rounds_json, duration_minutes, updated_at, revision, coach_request_fingerprint from workouts where session_reference = 'legacy-workout-486'")) {
            row.next();
            assertEquals("2026-08-20", row.getDate("workout_date").toString());
            assertEquals("Recorded sauna", row.getString("note"));
            assertEquals(true, row.getBoolean("sauna_session"));
            assertEquals("[12,8]", row.getString("sauna_rounds_json"));
            assertEquals(20, row.getInt("duration_minutes"));
            assertEquals(beforeUpdatedAt, row.getTimestamp("updated_at"));
            assertEquals(0, row.getLong("revision"));
            assertNull(row.getString("coach_request_fingerprint"));
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .target(MigrationVersion.fromVersion(target)).load();
    }
}
