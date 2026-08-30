package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinimalPersonalRecordDefaultsMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_minimal_record_defaults");

    @Test
    void removesUncustomizedRecordsAndNotificationsButKeepsExplicitOverrides() throws Exception {
        flyway(MigrationVersion.fromVersion("53")).migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('default@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500), ('custom@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into personal_record_settings (user_id, metric, mode) values (2, 'WORKOUT_REPETITIONS', 'MAXIMUM')");
            statement.executeUpdate("insert into personal_record_events (user_id, event_key, domain, metric, direction, kind, record_value, record_date, current_record, source_type, source_id) values (1, 'default-repetitions', 'WORKOUT', 'WORKOUT_REPETITIONS', 'MAXIMUM', 'IMPROVED', 12, '2026-08-30', true, 'WORKOUT', 1), (1, 'default-weight', 'BODY', 'BODY_WEIGHT', 'MINIMUM', 'IMPROVED', 80, '2026-08-30', true, 'WEIGHT', 1), (2, 'custom-repetitions', 'WORKOUT', 'WORKOUT_REPETITIONS', 'MAXIMUM', 'IMPROVED', 12, '2026-08-30', true, 'WORKOUT', 2)");
            statement.executeUpdate("insert into personal_record_snapshots (user_id, series_key, domain, metric, direction, value, record_date, source_type, source_id) values (1, 'default-repetitions', 'WORKOUT', 'WORKOUT_REPETITIONS', 'MAXIMUM', 12, '2026-08-30', 'WORKOUT', 1), (1, 'default-weight', 'BODY', 'BODY_WEIGHT', 'MINIMUM', 80, '2026-08-30', 'WEIGHT', 1), (2, 'custom-repetitions', 'WORKOUT', 'WORKOUT_REPETITIONS', 'MAXIMUM', 12, '2026-08-30', 'WORKOUT', 2)");
            statement.executeUpdate("insert into in_app_notifications (user_id, type, reminder_date, title, message, action_url, available_at, deduplication_key) values (1, 'PERSONAL_RECORD', '2026-08-30', 'New personal record', 'Most repetitions', '/records?tab=history&eventKey=default-repetitions', '2026-08-30 08:00:00', 'PERSONAL_RECORD:default-repetitions'), (1, 'PERSONAL_RECORD', '2026-08-30', 'New personal record', 'Lowest weight', '/records?tab=history&eventKey=default-weight', '2026-08-30 08:00:00', 'PERSONAL_RECORD:default-weight')");
        }

        flyway(null).migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            assertEquals(2, count(statement, "select count(*) from personal_record_events"));
            assertEquals(2, count(statement, "select count(*) from personal_record_snapshots"));
            assertEquals(1, count(statement, "select count(*) from in_app_notifications"));
        }
    }

    private int count(java.sql.Statement statement, String sql) throws Exception {
        try (var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
