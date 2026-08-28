package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RoutinePersonalRecordSettingMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_routine_record_settings");

    @Test
    void migratesTheGlobalRoutineSettingToEachRoutine() throws Exception {
        flyway("47").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('disabled@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500), ('enabled@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into routines (user_id, start_date, name, current_strike, best_strike) values (1, '2026-08-01 00:00:00', 'Disabled routine', 0, 0), (2, '2026-08-01 00:00:00', 'Enabled routine', 0, 0)");
            statement.executeUpdate("insert into personal_record_settings (user_id, metric, mode) values (1, 'ROUTINE_BEST_STREAK', 'DISABLED')");
        }

        flyway(null).migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement(); var routines = statement.executeQuery("select personal_records_enabled from routines order by id")) {
            routines.next();
            assertFalse(routines.getBoolean(1));
            routines.next();
            assertTrue(routines.getBoolean(1));
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
