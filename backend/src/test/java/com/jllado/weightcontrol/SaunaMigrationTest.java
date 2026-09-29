package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SaunaMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("sauna_migration");

    @Test void existingWorkoutsRemainOrdinarySessionsAfterAddingSaunaFields() throws Exception {
        flyway("90").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) VALUES ('sauna-migration@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("INSERT INTO workouts (user_id, workout_date, session_reference, duration_minutes) VALUES (1, '2026-09-01', UUID(), 30)");
        }
        flyway("91").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT sauna_session, sauna_rounds_json, planned_sauna_rounds_json, duration_minutes FROM workouts WHERE id = 1")) {
                assertTrue(rows.next());
                assertFalse(rows.getBoolean("sauna_session"));
                assertNull(rows.getString("sauna_rounds_json"));
                assertNull(rows.getString("planned_sauna_rounds_json"));
                assertEquals(30, rows.getInt("duration_minutes"));
            }
            statement.executeUpdate("INSERT INTO workouts (user_id, workout_date, session_reference, sauna_session, sauna_rounds_json) VALUES (1, '2026-09-02', UUID(), TRUE, '[12,8]')");
        }
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
