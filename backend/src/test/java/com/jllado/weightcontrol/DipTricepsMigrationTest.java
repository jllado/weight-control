package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DipTricepsMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("dip_triceps_migration");

    @Test void reclassifiesOnlyWeightedDipWithoutRewritingSavedWorkoutsOrPlans() throws Exception {
        flyway("99").migrate();
        try (var connection = DATABASE.createConnection(""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) VALUES ('dip@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            sql.executeUpdate("INSERT INTO workouts (user_id, workout_date, session_reference, start_time, duration_minutes, planned_targets_json) VALUES (1, '2026-10-02', UUID(), '08:00', 45, '[{\"exerciseName\":\"Weighted dip\",\"segments\":[{\"repetitions\":10}]}]')");
            sql.executeUpdate("INSERT INTO workout_lines (workout_id, exercise_id, position) SELECT 1, id, 0 FROM exercises WHERE name = 'Weighted dip'");
            sql.executeUpdate("INSERT INTO workout_segments (workout_line_id, position, repetitions, weight) VALUES (1, 0, 10, 10.50), (1, 1, 8, 11.00), (1, 2, 6, 12.00), (1, 3, 5, 12.50)");
            sql.executeUpdate("INSERT INTO workout_plans (user_id, start_date, review_date, days_json, created_at, updated_at, update_token) VALUES (1, '2026-09-26', '2026-10-02', '[{\"day\":\"FRIDAY\",\"rest\":false,\"sessions\":[{\"exercises\":[{\"exerciseName\":\"Weighted dip\"}]}]}]', NOW(6), NOW(6), UUID())");
        }
        var expected = snapshots();
        var dip = expected.get("exercises").stream().filter(row -> row.get("name").equals("Weighted dip")).findFirst().orElseThrow();
        assertEquals("CHEST", dip.put("primary_muscle_group", "TRICEPS"));
        assertEquals(1, flyway("100").migrate().migrationsExecuted);
        var actual = snapshots();
        var actualDip = actual.get("exercises").stream().filter(row -> row.get("name").equals("Weighted dip")).findFirst().orElseThrow();
        // MariaDB advances ON UPDATE CURRENT_TIMESTAMP when this changed exercise row is written.
        dip.put("updated_at", actualDip.get("updated_at"));
        assertEquals(expected, actual);
        assertEquals(0, flyway("100").migrate().migrationsExecuted);
    }

    private Map<String, List<Map<String, String>>> snapshots() throws Exception {
        var result = new LinkedHashMap<String, List<Map<String, String>>>();
        try (var connection = DATABASE.createConnection(""); var sql = connection.createStatement()) {
            for (var table : List.of("exercises", "workouts", "workout_lines", "workout_segments", "workout_plans")) {
                var records = new ArrayList<Map<String, String>>();
                try (var rows = sql.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    while (rows.next()) {
                        var values = new LinkedHashMap<String, String>();
                        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) values.put(rows.getMetaData().getColumnName(i), rows.getString(i));
                        records.add(values);
                    }
                }
                result.put(table, records);
            }
        }
        return result;
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
