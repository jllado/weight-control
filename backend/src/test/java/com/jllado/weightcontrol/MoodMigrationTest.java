package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MoodMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_mood_migration");

    @Test
    void migrationCopiesExistingMoodIntoEveryPeriod() throws Exception {
        flyway("21").migrate();
        Timestamp createdAt = Timestamp.valueOf("2026-08-10 08:00:00");
        Timestamp updatedAt = Timestamp.valueOf("2026-08-10 09:00:00");

        try (var connection = DATABASE.createConnection("");
             var user = connection.prepareStatement("""
                 insert into users (
                     email,
                     typical_calories_saturday,
                     typical_calories_sunday,
                     typical_calories_monday,
                     typical_calories_tuesday,
                     typical_calories_wednesday,
                     typical_calories_thursday,
                     typical_calories_friday
                 ) values ('mood-migration@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                 """);
             var mood = connection.prepareStatement("""
                 insert into moods (user_id, mood_date, value, note, created_at, updated_at)
                 values (1, '2026-08-10', 4, 'Historical note', ?, ?)
                 """)) {
            user.executeUpdate();
            mood.setTimestamp(1, createdAt);
            mood.setTimestamp(2, updatedAt);
            mood.executeUpdate();
        }

        flyway(null).migrate();

        List<String> periods = new ArrayList<>();
        try (var connection = DATABASE.createConnection("");
             var statement = connection.prepareStatement("select period, value, note, created_at, updated_at from moods order by period");
             var result = statement.executeQuery()) {
            while (result.next()) {
                periods.add(result.getString("period"));
                assertEquals(4, result.getInt("value"));
                assertEquals("Historical note", result.getString("note"));
                assertEquals(createdAt, result.getTimestamp("created_at"));
                assertEquals(updatedAt, result.getTimestamp("updated_at"));
            }
        }

        assertEquals(List.of("EVENING", "MIDDAY", "MORNING"), periods);
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
            .dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }
}
