package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ExerciseEstimatesMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("exercise_estimates");

    @Test void addsOptionalEstimatesAndRejectsNonpositiveValuesWithoutChangingTheCatalog() throws Exception {
        flyway("104").migrate();
        String before;
        String workoutBefore;
        try (Connection connection = DATABASE.createConnection(""); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                VALUES ('estimate-migration@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                """);
            statement.executeUpdate("INSERT INTO workouts (user_id, workout_date, session_reference) VALUES (1, '2040-01-01', UUID())");
            statement.executeUpdate("INSERT INTO workout_lines (workout_id, exercise_id, position, exercise_duration_seconds) SELECT 1, id, 0, 42 FROM exercises WHERE name = 'Squat'");
            statement.executeUpdate("INSERT INTO workout_segments (workout_line_id, position, repetitions, weight) VALUES (1, 0, 12, 20)");
            before = catalog(statement);
            workoutBefore = workout(statement);
        }
        flyway("105").migrate();
        try (Connection connection = DATABASE.createConnection(""); Statement statement = connection.createStatement()) {
            assertEquals(before, catalog(statement));
            assertEquals(workoutBefore, workout(statement));
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM exercises WHERE estimated_seconds_per_set IS NOT NULL")) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            }
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE exercises SET estimated_seconds_per_set = 0 WHERE name = 'Squat'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE exercises SET estimated_seconds_per_set = -1 WHERE name = 'Squat'"));
            assertEquals(1, statement.executeUpdate("UPDATE exercises SET estimated_seconds_per_set = 60 WHERE name = 'Squat'"));
            assertEquals(1, statement.executeUpdate("UPDATE exercises SET estimated_seconds_per_set = NULL WHERE name = 'Squat'"));
            try (var rows = statement.executeQuery("SELECT estimated_seconds_per_set FROM exercises WHERE name = 'Squat'")) {
                assertTrue(rows.next()); assertNull(rows.getObject(1));
            }
        }
    }

    private String workout(Statement statement) throws SQLException {
        try (var rows = statement.executeQuery("SELECT workout_lines.exercise_duration_seconds, workout_segments.repetitions, workout_segments.weight, workout_segments.skipped FROM workout_lines JOIN workout_segments ON workout_segments.workout_line_id = workout_lines.id WHERE workout_lines.workout_id = 1")) {
            assertTrue(rows.next()); return rows.getInt(1) + "|" + rows.getInt(2) + "|" + rows.getBigDecimal(3) + "|" + rows.getBoolean(4);
        }
    }

    private String catalog(Statement statement) throws SQLException {
        var result = new StringBuilder();
        try (var rows = statement.executeQuery("SELECT id, name, description, tracking_mode, exercise_type, built_in_image_key, created_at, updated_at FROM exercises ORDER BY id")) {
            while (rows.next()) for (int index = 1; index <= 8; index++) result.append(rows.getString(index)).append('|');
        }
        return result.toString();
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").target(target).load();
    }
}
