package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.ArrayList;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RoutineAutomaticTriggersMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("routine_automatic_triggers_migration");

    @Test void addsAutomaticTriggerDefaultsAndEvidenceConstraintsForLegacyRows() throws Exception {
        flyway("92").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('automatic-routine@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into routines (user_id, start_date, name, current_strike, best_strike) values (1, '2026-08-01 00:00:00', 'Fruit', 0, 0)");
            statement.executeUpdate("insert into routine_checkins (routine_id, checked_at) values (1, '2026-08-10 12:00:00')");
            statement.executeUpdate("insert into meals (user_id, meal_date, meal_type, meal_sequence, calories) values (1, '2026-08-10', 'LUNCH', 1, 100)");
            statement.executeUpdate("insert into meal_dishes (meal_id, position, name, calories, quantity, unit, reference_quantity, reference_calories) values (1, 1, 'Apple', 100, 1, 'UNIT', 1, 100)");
            statement.executeUpdate("insert into catalog_foods (user_id, normalized_name, name, quantity, unit, calories, reference_quantity, reference_calories) values (1, 'apple', 'Apple', 1, 'UNIT', 100, 1, 100)");
            statement.executeUpdate("insert into dish_recipes (user_id, name, normalized_name, servings) values (1, 'Fruit bowl', 'fruit bowl', 1)");
            statement.executeUpdate("insert into recipe_ingredients (recipe_id, position, name, quantity, unit, calories, reference_quantity, reference_calories) values (1, 1, 'Apple', 1, 'UNIT', 100, 1, 100)");
        }
        flyway(null).migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var row = statement.executeQuery("select automatic_trigger from routines where id = 1")) { assertTrue(row.next()); assertEquals("NONE", row.getString(1)); }
            try (var row = statement.executeQuery("select manual_completion from routine_checkins where routine_id = 1")) { assertTrue(row.next()); assertTrue(row.getBoolean(1)); }
            for (var table : new String[] {"catalog_foods", "meal_dishes", "recipe_ingredients"}) {
                try (var row = statement.executeQuery("select fruit from " + table + " limit 1")) { assertTrue(row.next(), table); assertFalse(row.getBoolean(1), table); }
            }
            statement.executeUpdate("insert into routine_automatic_evidence (routine_id, source_kind, source_key, event_date) values (1, 'MEAL', '1', '2026-08-10')");
            assertThrows(SQLException.class, () -> statement.executeUpdate("insert into routine_automatic_evidence (routine_id, source_kind, source_key, event_date) values (1, 'MEAL', '1', '2026-08-10')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("insert into routine_automatic_evidence (routine_id, source_kind, source_key, event_date) values (999, 'MEAL', '2', '2026-08-10')"));
            try (var rows = statement.executeQuery("show index from routine_automatic_evidence where Key_name = 'idx_routine_automatic_evidence_day'")) {
                var columns = new ArrayList<String>();
                while (rows.next()) columns.add(rows.getString("Column_name"));
                assertEquals(java.util.List.of("routine_id", "event_date"), columns);
            }
            statement.executeUpdate("delete from routines where id = 1");
            try (var rows = statement.executeQuery("select count(*) from routine_automatic_evidence")) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
        }
    }
    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
