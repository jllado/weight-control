package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RoutineCompletionSettingsMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("routine_completion_settings");

    @Test void preservesAssignedRulesEnabledStateAndAllExistingHistory() throws Exception {
        flyway("105").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('routine-settings@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            var rules = List.of("NONE", "FAST_OVER_12_HOURS", "FRUIT_MEAL", "CARDIO_WORKOUT", "STRENGTH_WORKOUT", "STRETCHING_WORKOUT", "MCGILL_BIG_THREE");
            for (int i = 0; i < rules.size(); i++) {
                statement.executeUpdate("insert into routines (user_id, start_date, last_time_date, name, current_strike, best_strike, automatic_trigger) values (1, '2026-08-01 00:00:00', '2026-08-12 00:00:00', 'Existing " + i + "', 1, 20, '" + rules.get(i) + "')");
                statement.executeUpdate("insert into routine_checkins (routine_id, checked_at, manual_completion) values (" + (i + 1) + ", '2026-08-12 00:00:00', " + (i == 0) + ")");
                statement.executeUpdate("insert into routine_reminders (routine_id, reminder_time) values (" + (i + 1) + ", '08:00:00')");
                statement.executeUpdate("insert into routine_types (routine_id, type) values (" + (i + 1) + ", 'MIND')");
                if (i > 0) statement.executeUpdate("insert into routine_automatic_evidence (routine_id, source_kind, source_key, event_date) values (" + (i + 1) + ", 'MEAL', 'original', '2026-08-12')");
            }
            String routineQuery = "select id, user_id, start_date, last_time_date, name, current_strike, best_strike, personal_records_enabled, automatic_trigger, created_at, updated_at from routines order by id";
            var before = snapshot(connection, routineQuery);
            var tables = List.of("routine_checkins", "routine_reminders", "routine_types", "routine_automatic_evidence");
            var history = new ArrayList<List<List<String>>>();
            for (String table : tables) history.add(snapshot(connection, "select * from " + table + " order by routine_id"));
            flyway(null).migrate();
            assertEquals(before, snapshot(connection, routineQuery));
            for (int i = 0; i < tables.size(); i++) assertEquals(history.get(i), snapshot(connection, "select * from " + tables.get(i) + " order by routine_id"));
            try (var rows = statement.executeQuery("select automatic_trigger, automatic_completion_enabled from routines order by id")) {
                for (String rule : rules) {
                    assertTrue(rows.next());
                    assertEquals(rule, rows.getString(1));
                    assertEquals(!rule.equals("NONE"), rows.getBoolean(2));
                }
                assertFalse(rows.next());
            }
        }
    }

    private List<List<String>> snapshot(Connection connection, String query) throws Exception {
        var result = new ArrayList<List<String>>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) {
                var row = new ArrayList<String>();
                for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.add(rows.getString(i));
                result.add(row);
            }
        }
        return result;
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }
}
