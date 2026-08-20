package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BackPainPeriodMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_back_pain_period");

    @Test
    void infersPeriodsAndKeepsOnlyTheLatestEpisodePerPeriod() throws Exception {
        flyway("27").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('periods@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, region, side, severity, note) values (1, '2026-08-01', '11:59:00', 'LOWER', 'LEFT', 'MILD', 'Morning boundary')");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, region, side, severity, note) values (1, '2026-08-02', '12:00:00', 'LOWER', 'LEFT', 'MILD', 'Midday start')");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, region, side, severity, note) values (1, '2026-08-03', '17:59:00', 'LOWER', 'LEFT', 'MILD', 'Midday end')");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, region, side, severity, note) values (1, '2026-08-04', '18:00:00', 'LOWER', 'LEFT', 'MILD', 'Evening boundary')");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, region, severity, note) values (1, '2026-08-05', 'LOWER', 'MILD', 'No time')");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, region, side, severity, note) values (1, '2026-08-06', '08:00:00', 'LOWER', 'LEFT', 'MILD', 'Older')");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, region, side, severity, note) values (1, '2026-08-06', '09:00:00', 'LOWER', 'LEFT', 'MODERATE', 'Same-time older ID')");
            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, region, side, severity, note) values (1, '2026-08-06', '09:00:00', 'LOWER', 'RIGHT', 'SEVERE', 'Latest ID')");
        }

        flyway(null).migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement(); var result = statement.executeQuery("select period, note from back_pain_episodes order by episode_date")) {
            List<MigratedEpisode> episodes = new ArrayList<>();
            while (result.next()) {
                episodes.add(new MigratedEpisode(result.getString("period"), result.getString("note")));
            }
            assertEquals(List.of(
                new MigratedEpisode("MORNING", "Morning boundary"),
                new MigratedEpisode("MIDDAY", "Midday start"),
                new MigratedEpisode("MIDDAY", "Midday end"),
                new MigratedEpisode("EVENING", "Evening boundary"),
                new MigratedEpisode(null, "No time"),
                new MigratedEpisode("MORNING", "Latest ID")
            ), episodes);

            statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, period, region, side, severity) values (1, '2026-08-06', '10:00:00', 'MORNING', 'UPPER', 'CENTER', 'EXTREME')");
            assertThrows(SQLException.class, () -> statement.executeUpdate("insert into back_pain_episodes (user_id, episode_date, episode_time, period, region, side, severity) values (1, '2026-08-06', '10:05:00', 'MORNING', 'LOWER', 'RIGHT', 'EXTREME')"));
            assertNull(episodes.get(4).period());
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private record MigratedEpisode(String period, String note) {
    }
}
