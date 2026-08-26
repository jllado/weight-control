package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DecisionPersonalRecordMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_decision_records");

    @Test
    void removesDecisionRecordArtifactsAndKeepsDecisionOutcomes() throws Exception {
        flyway(MigrationVersion.fromVersion("42")).migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('decision-records@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into decision_outcomes (user_id, outcome_date, outcome) values (1, '2026-08-26', 'WIN')");
            statement.executeUpdate("insert into personal_record_settings (user_id, metric, mode) values (1, 'DECISION_WIN_RATE', 'MAXIMUM'), (1, 'BODY_WEIGHT', 'MINIMUM')");
            statement.executeUpdate("insert into personal_record_snapshots (user_id, series_key, domain, metric, direction, subject_type, subject_label, value, record_date, source_type, source_id) values (1, 'DECISION_WIN_RATE_MAXIMUM:BEHAVIOR:Decisions', 'BEHAVIOR', 'DECISION_WIN_RATE_MAXIMUM', 'MAXIMUM', 'BEHAVIOR', 'Decisions', 100, '2026-08-26', 'DECISION_OUTCOME', 1), (1, 'BEHAVIOR_CHANGE_PERCENT_MAXIMUM:ROLLING_30:30-day WIN-rate change', 'BEHAVIOR', 'BEHAVIOR_CHANGE_PERCENT_MAXIMUM', 'MAXIMUM', 'ROLLING_30', '30-day WIN-rate change', 10, '2026-08-26', 'DERIVED_PERIOD', null), (1, 'BODY_WEIGHT', 'BODY', 'BODY_WEIGHT', 'MINIMUM', null, null, 80, '2026-08-26', 'WEIGHT', 1)");
            statement.executeUpdate("insert into in_app_notifications (user_id, type, reminder_date, title, message, action_url, available_at, deduplication_key) values (1, 'PERSONAL_RECORD', '2026-08-26', 'New personal record', 'Decisions: 1 decisions', '/records?tab=history&eventKey=decision', '2026-08-26 08:00:00', 'PERSONAL_RECORD:decision'), (1, 'PERSONAL_RECORD', '2026-08-26', 'New personal record', '30-day WIN-rate change: 10 %', '/records?tab=history&eventKey=change', '2026-08-26 08:00:00', 'PERSONAL_RECORD:change'), (1, 'PERSONAL_RECORD', '2026-08-26', 'New personal record', 'Body: 80 kg', '/records?tab=history&eventKey=body', '2026-08-26 08:00:00', 'PERSONAL_RECORD:body')");
        }

        flyway(null).migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            assertEquals(1, count(statement, "select count(*) from decision_outcomes"));
            assertEquals(1, count(statement, "select count(*) from personal_record_settings"));
            assertEquals(1, count(statement, "select count(*) from personal_record_snapshots"));
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
