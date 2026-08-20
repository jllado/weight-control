package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BackPainLocationMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_back_pain_location");

    @Test
    void allowsSeveralLocationsAndRejectsAnExactDuplicateInOnePeriod() throws Exception {
        flyway("32").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('locations@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, period, region, side, severity) values (1, '2026-08-20', '08:00:00', 'MORNING', 'LOWER', 'LEFT', 'MILD')");
        }

        flyway(null).migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, period, region, side, severity) values (1, '2026-08-20', '08:05:00', 'MORNING', 'UPPER', 'LEFT', 'MODERATE')");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, period, region, side, severity) values (1, '2026-08-20', '08:10:00', 'MORNING', 'LOWER', 'RIGHT', 'SEVERE')");
            assertThrows(SQLException.class, () -> statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, period, region, side, severity) values (1, '2026-08-20', '08:15:00', 'MORNING', 'LOWER', 'LEFT', 'EXTREME')"));
            try (var result = statement.executeQuery("select count(*) from back_pain_episodes")) {
                result.next();
                assertEquals(3, result.getInt(1));
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
