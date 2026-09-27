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
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").target(target).load();
    }
}
