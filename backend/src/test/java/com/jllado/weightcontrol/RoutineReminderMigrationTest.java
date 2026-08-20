package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RoutineReminderMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_routine_reminders");

    @Test
    void migratesExistingReminderStateAndNotificationIdentity() throws Exception {
        flyway("33").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('reminders@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into routines (user_id, start_date, name, reminder_time, reminder_snoozed_until, current_strike, best_strike) values (1, '2026-08-01 00:00:00', 'Meditation', '07:30:00', '2026-08-20 08:00:00', 0, 0)");
            statement.executeUpdate("insert into routines (user_id, start_date, name, current_strike, best_strike) values (1, '2026-08-01 00:00:00', 'Walking', 0, 0)");
            statement.executeUpdate("insert into in_app_notifications (user_id, type, routine_id, reminder_date, title, message, available_at, deduplication_key) values (1, 'ROUTINE', 1, '2026-08-20', 'Routine reminder', 'Meditation', '2026-08-20 07:30:00', 'ROUTINE:1:2026-08-20')");
            statement.executeUpdate("insert into in_app_notifications (user_id, type, routine_id, reminder_date, title, message, available_at, deduplication_key) values (1, 'ROUTINE', 2, '2026-08-20', 'Routine reminder', 'Walking', '2026-08-20 07:30:00', 'ROUTINE:2:2026-08-20')");
        }

        flyway(null).migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var reminder = statement.executeQuery("select id, routine_id, reminder_time, reminder_snoozed_until from routine_reminders")) {
                reminder.next();
                assertEquals(1L, reminder.getLong("routine_id"));
                assertEquals("07:30:00", reminder.getTime("reminder_time").toString());
                assertEquals("2026-08-20 08:00:00.0", reminder.getTimestamp("reminder_snoozed_until").toString());
                long reminderId = reminder.getLong("id");
                try (var notification = statement.executeQuery("select routine_reminder_id, deduplication_key from in_app_notifications where type = 'ROUTINE'")) {
                    notification.next();
                    assertEquals(reminderId, notification.getLong("routine_reminder_id"));
                    assertEquals("ROUTINE:" + reminderId + ":2026-08-20", notification.getString("deduplication_key"));
                    assertEquals(false, notification.next());
                }
            }
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
