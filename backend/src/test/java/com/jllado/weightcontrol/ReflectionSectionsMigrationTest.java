package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ReflectionSectionsMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("reflection_sections_migration");

    @Test
    void retainsLegacyReflectionWithoutBackfillingSections() throws Exception {
        flyway("88").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('reflection-migration@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("""
                insert into dashboard_reflections (user_id, reflection_date, window_start, window_end, generated_at, model,
                    title, summary, positive_signals_json, watchouts_json, next_actions_json, plan_progress_score, plan_progress_rationale)
                values (1, '2026-08-20', '2026-05-23', '2026-08-20', '2026-08-20 12:00:00', 'ChatGPT',
                    'Legacy reflection', 'Original summary', '["Positive"]', '["Watch"]', '["Action"]', 7, 'Original rationale')
                """);
        }
        flyway("89").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("select * from dashboard_reflections")) {
            assertTrue(result.next());
            assertEquals("Original summary", result.getString("summary"));
            assertEquals(7, result.getInt("plan_progress_score"));
            assertEquals("Original rationale", result.getString("plan_progress_rationale"));
            assertEquals("[\"Action\"]", result.getString("next_actions_json"));
            for (var column : new String[] {"meals_summary", "meals_next_action", "workouts_summary", "workouts_next_action"}) {
                assertNull(result.getString(column));
            }
            assertFalse(result.next());
        }
        flyway("118").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var original = statement.executeQuery("select plan_progress_rationale from dashboard_reflections")) {
                assertTrue(original.next());
                assertEquals("Original rationale", original.getString(1));
            }
            try (var column = statement.executeQuery("select character_maximum_length, is_nullable from information_schema.columns where table_schema = database() and table_name = 'dashboard_reflections' and column_name = 'plan_progress_rationale'")) {
                assertTrue(column.next());
                assertEquals(500, column.getInt(1));
                assertEquals("YES", column.getString(2));
            }
            var rationale = "R".repeat(500);
            try (var update = connection.prepareStatement("update dashboard_reflections set plan_progress_rationale = ?")) {
                update.setString(1, rationale);
                assertEquals(1, update.executeUpdate());
            }
            try (var expanded = statement.executeQuery("select plan_progress_rationale from dashboard_reflections")) {
                assertTrue(expanded.next());
                assertEquals(rationale, expanded.getString(1));
            }
            statement.executeUpdate("update dashboard_reflections set plan_progress_rationale = null");
            try (var nullable = statement.executeQuery("select plan_progress_rationale from dashboard_reflections")) {
                assertTrue(nullable.next());
                assertNull(nullable.getString(1));
            }
        }

    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").target(target).load();
    }
}
