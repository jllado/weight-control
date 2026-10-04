package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TrainingBalanceMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("training_balance_migration");

    @Test void classifiesAllCatalogRowsIncludingAdditionalEntriesWithoutRewritingRecordedData() throws Exception {
        flyway("97").migrate();
        try (var connection = DATABASE.createConnection(""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO exercises (name, description, tracking_mode, exercise_type, custom_image_path) VALUES ('Banded clamshell', 'My hip abduction', 'REPS', 'TRAINING', 'exercise-images/custom.jpg')");
            sql.executeUpdate("INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) VALUES ('balance@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            sql.executeUpdate("INSERT INTO workouts (user_id, workout_date, session_reference, start_time, duration_minutes, planned_targets_json) VALUES (1, '2026-10-04', UUID(), '08:00', 45, '[{\"exerciseName\":\"Push-up\",\"segments\":[{\"repetitions\":12}]}]'), (1, '2026-10-04', UUID(), null, null, null)");
            sql.executeUpdate("INSERT INTO workout_lines (workout_id, exercise_id, position) SELECT 1, id, 0 FROM exercises WHERE name = 'Push-up'");
            sql.executeUpdate("INSERT INTO workout_segments (workout_line_id, position, repetitions, weight) VALUES (1, 0, 12, 10.50), (1, 1, 8, 11.00)");
            sql.executeUpdate("INSERT INTO workout_plans (user_id, start_date, review_date, days_json, created_at, updated_at, update_token) VALUES (1, '2026-10-03', '2026-10-09', '[{\"day\":\"MONDAY\",\"rest\":true}]', NOW(6), NOW(6), UUID())");
        }
        var before = snapshots(List.of("workouts", "workout_lines", "workout_segments", "workout_plans"));
        flyway("98").migrate();
        assertEquals(before, snapshots(List.of("workouts", "workout_lines", "workout_segments", "workout_plans")));
        try (var connection = DATABASE.createConnection(""); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM exercises WHERE exercise_type = 'TRAINING' AND tracking_mode IN ('REPS', 'SECONDS') AND primary_muscle_group IS NOT NULL")) { assertTrue(rows.next()); assertEquals(29, rows.getInt(1)); }
            try (var rows = sql.executeQuery("SELECT primary_muscle_group, description, custom_image_path FROM exercises WHERE name = 'Banded clamshell'")) { assertTrue(rows.next()); assertEquals("GLUTES", rows.getString(1)); assertEquals("My hip abduction", rows.getString(2)); assertEquals("exercise-images/custom.jpg", rows.getString(3)); }
            try (var rows = sql.executeQuery("SELECT primary_muscle_group FROM exercises WHERE name = 'Jefferson curl'")) { assertTrue(rows.next()); assertEquals("BACK", rows.getString(1)); }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM exercises WHERE (exercise_type <> 'TRAINING' OR tracking_mode = 'CARDIO') AND primary_muscle_group IS NOT NULL")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
            assertThrows(java.sql.SQLException.class, () -> sql.executeUpdate("INSERT INTO exercises (name, description, tracking_mode, exercise_type) VALUES ('Unclassified strength', 'New', 'REPS', 'TRAINING')"));
            sql.executeUpdate("INSERT INTO exercises (name, description, tracking_mode, exercise_type, primary_muscle_group) VALUES ('New classified exercise', 'New', 'REPS', 'TRAINING', 'FOREARMS')");
        }
        assertEquals(0, flyway("98").migrate().migrationsExecuted);
    }
    private Map<String, List<List<String>>> snapshots(List<String> tables) throws Exception {
        var result = new LinkedHashMap<String, List<List<String>>>();
        try (var connection = DATABASE.createConnection(""); var sql = connection.createStatement()) {
            for (var table : tables) {
                var records = new ArrayList<List<String>>();
                try (var rows = sql.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    while (rows.next()) { var values = new ArrayList<String>(); for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) values.add(rows.getString(i)); records.add(values); }
                }
                result.put(table, records);
            }
        }
        return result;
    }
    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
