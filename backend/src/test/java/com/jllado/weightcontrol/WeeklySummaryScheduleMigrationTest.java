package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WeeklySummaryScheduleMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("weekly_schedule_migration");

    @Test
    void existingAndNewAccountsKeepMondayDefaultsAndSavedEvidence() throws Exception {
        flyway("103").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                values ('existing@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                """);
            statement.executeUpdate("insert into weekly_summaries (user_id, friday_date, snapshot_json, created_at) values (1, '2026-08-14', '{\"schemaVersion\":2}', '2026-08-17 06:00:00')");
        }
        flyway("104").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                values ('new@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                """);
            try (var rows = statement.executeQuery("select weekly_summary_delivery_day, weekly_summary_delivery_time from users order by id")) {
                for (int index = 0; index < 2; index++) {
                    assertTrue(rows.next());
                    assertEquals("MONDAY", rows.getString(1));
                    assertEquals("08:00:00", rows.getTime(2).toString());
                }
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("select snapshot_json, scheduled_emailed_at from weekly_summaries")) {
                assertTrue(rows.next());
                assertEquals("{\"schemaVersion\":2}", rows.getString(1));
                assertNull(rows.getTimestamp(2));
                assertFalse(rows.next());
            }
        }
        assertEquals(0, flyway("104").migrate().migrationsExecuted);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").target(target).load();
    }
}
