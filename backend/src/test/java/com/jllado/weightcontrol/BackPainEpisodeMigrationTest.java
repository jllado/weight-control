package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BackPainEpisodeMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control");

    @Test
    void migratesWorstPainRegionsAndDropsOldTable() throws Exception {
        dataSource(MigrationVersion.fromVersion("22")).migrate();
        try (Connection connection = DATABASE.createConnection("")) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('owner@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
                statement.executeUpdate("insert into back_statuses (user_id, status_date, lower_pain, lower_stiffness, lower_activity_limitation, middle_pain, middle_stiffness, middle_activity_limitation, upper_pain, upper_stiffness, upper_activity_limitation, note) values (1, '2026-08-08', 5, 1, 2, 5, 2, 3, 2, 3, 4, 'Tied pain')");
                statement.executeUpdate("insert into back_statuses (user_id, status_date, lower_pain, lower_stiffness, lower_activity_limitation, middle_pain, middle_stiffness, middle_activity_limitation, upper_pain, upper_stiffness, upper_activity_limitation, note) values (1, '2026-08-09', 0, 5, 5, 0, 5, 5, 0, 5, 5, 'No pain')");
                statement.executeUpdate("insert into back_statuses (user_id, status_date, lower_pain, lower_stiffness, lower_activity_limitation, middle_pain, middle_stiffness, middle_activity_limitation, upper_pain, upper_stiffness, upper_activity_limitation, note) values (1, '2026-08-10', 1, 1, 1, 4, 4, 4, 9, 9, 9, 'Upper pain')");
            }
        }

        dataSource(null).migrate();

        try (Connection connection = DATABASE.createConnection("")) {
            List<MigratedEpisode> episodes = new ArrayList<>();
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("select episode_date, episode_time, region, side, pain, note from back_pain_episodes order by episode_date, region")) {
                while (result.next()) {
                    episodes.add(new MigratedEpisode(
                        result.getDate("episode_date").toLocalDate(),
                        result.getTime("episode_time"),
                        result.getString("region"),
                        result.getString("side"),
                        result.getInt("pain"),
                        result.getString("note")
                    ));
                }
            }
            assertEquals(3, episodes.size());
            assertEquals(new MigratedEpisode(LocalDate.of(2026, 8, 8), null, "LOWER", null, 5, "Tied pain"), episodes.get(0));
            assertEquals(new MigratedEpisode(LocalDate.of(2026, 8, 8), null, "MIDDLE", null, 5, "Tied pain"), episodes.get(1));
            assertEquals(new MigratedEpisode(LocalDate.of(2026, 8, 10), null, "UPPER", null, 9, "Upper pain"), episodes.get(2));
            assertNull(episodes.getFirst().time());
            assertFalse(tableExists(connection, "back_statuses"));
        }
    }

    private Flyway dataSource(MigrationVersion target) {
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private record MigratedEpisode(LocalDate date, java.sql.Time time, String region, String side, Integer pain, String note) {
    }
}
