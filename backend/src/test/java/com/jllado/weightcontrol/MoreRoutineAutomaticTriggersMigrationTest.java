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
class MoreRoutineAutomaticTriggersMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("more_routine_automatic_triggers");

    @Test void assignsAndEnablesOnlyExactUnassignedNamesWithoutChangingIdentityOrHistory() throws Exception {
        flyway("106").migrate();
        var names = List.of("SLEEP 7 HOURS", "Kiwi", "Wake up early", "Night stretching", "Sitting-Rising");
        var triggers = List.of("SLEEP_BED_AT_LEAST_7_HOURS", "KIWI_MEAL", "WAKE_BEFORE_7", "NIGHT_STRETCHING_WORKOUT", "SITTING_RISING_WORKOUT");
        var otherNames = List.of("sleep 7 hours", "kiwi", "wake UP early", "night Stretching", "sitting-rising", " Kiwi", "Kiwi ", "Kiwi snack", "Custom");
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            for (int user = 1; user <= 2; user++) statement.executeUpdate("insert into users (id, email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values (" + user + ", 'routine-rules-" + user + "@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            int id = 1;
            for (String name : names) seedRoutine(connection, id++, 1, name, "NONE", false);
            for (int i = 0; i < names.size(); i++) seedRoutine(connection, id++, 1, names.get(i), "FRUIT_MEAL", i % 2 == 0);
            for (String name : otherNames) seedRoutine(connection, id++, 1, name, "NONE", false);
            int secondUserRoutine = id;
            seedRoutine(connection, id, 2, "Kiwi", "NONE", false);

            String identityQuery = "select id, user_id, start_date, last_time_date, name, current_strike, best_strike, personal_records_enabled, created_at, updated_at from routines order by id";
            var identity = snapshot(connection, identityQuery);
            var historyTables = List.of("routine_checkins", "routine_reminders", "routine_types", "routine_automatic_evidence");
            var history = new ArrayList<List<List<String>>>();
            for (String table : historyTables) history.add(snapshot(connection, "select * from " + table + " order by routine_id"));
            var preassigned = snapshot(connection, "select id, automatic_trigger, automatic_completion_enabled from routines where automatic_trigger <> 'NONE' order by id");

            flyway(null).migrate();

            assertEquals(identity, snapshot(connection, identityQuery));
            for (int i = 0; i < historyTables.size(); i++) assertEquals(history.get(i), snapshot(connection, "select * from " + historyTables.get(i) + " order by routine_id"), historyTables.get(i));
            assertEquals(preassigned, snapshot(connection, "select id, automatic_trigger, automatic_completion_enabled from routines where id between 6 and 10 order by id"));
            for (int i = 0; i < names.size(); i++) assertEquals(List.of(List.of(triggers.get(i), "1")), snapshot(connection, "select automatic_trigger, automatic_completion_enabled from routines where id = " + (i + 1)), names.get(i));
            assertEquals(List.of(List.of("KIWI_MEAL", "1")), snapshot(connection, "select automatic_trigger, automatic_completion_enabled from routines where id = " + secondUserRoutine));
            for (int other = 11; other < secondUserRoutine; other++) assertEquals(List.of(List.of("NONE", "0")), snapshot(connection, "select automatic_trigger, automatic_completion_enabled from routines where id = " + other));
        }
    }

    private void seedRoutine(Connection connection, int id, int user, String name, String trigger, boolean enabled) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("insert into routines (id, user_id, start_date, last_time_date, name, current_strike, best_strike, automatic_trigger, automatic_completion_enabled, created_at, updated_at) values (" + id + ", " + user + ", '2020-08-01 00:00:00', '2020-08-12 00:00:00', '" + name + "', 1, 20, '" + trigger + "', " + enabled + ", '2020-08-01 00:00:00', '2020-08-12 00:00:00')");
            statement.executeUpdate("insert into routine_checkins (routine_id, checked_at, manual_completion) values (" + id + ", '2020-08-12 00:00:00', " + (id % 2 == 0) + ")");
            statement.executeUpdate("insert into routine_reminders (routine_id, reminder_time, reminder_snoozed_until) values (" + id + ", '08:00:00', '2020-08-12 09:00:00')");
            statement.executeUpdate("insert into routine_types (routine_id, type) values (" + id + ", 'MIND')");
            statement.executeUpdate("insert into routine_automatic_evidence (routine_id, source_kind, source_key, event_date) values (" + id + ", 'MEAL', 'preserved-" + id + "', '2020-08-12')");
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
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
