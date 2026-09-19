package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MealRatingMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_meal_rating_migration");

    @Test
    void convertsExistingRatingsProportionallyAndEnforcesTenPointScale() throws Exception {
        flyway("76").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('rating-migration@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("""
                insert into meals (user_id, meal_date, meal_type, meal_sequence, calories, rating) values
                (1, '2026-08-12', 'SNACK', 1, 100, null),
                (1, '2026-08-12', 'SNACK', 2, 100, 1),
                (1, '2026-08-12', 'SNACK', 3, 100, 4),
                (1, '2026-08-12', 'SNACK', 4, 100, 5)
                """);
        }
        flyway(null).migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("select rating from meals order by meal_sequence")) {
                result.next();
                assertNull(result.getObject("rating"));
                for (int expected : new int[] {2, 8, 10}) {
                    result.next();
                    assertEquals(expected, result.getInt("rating"));
                }
            }
            assertEquals(1, statement.executeUpdate("update meals set rating = 1 where meal_sequence = 1"));
            assertEquals(1, statement.executeUpdate("update meals set rating = 10 where meal_sequence = 1"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("update meals set rating = 0 where meal_sequence = 1"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("update meals set rating = 11 where meal_sequence = 1"));
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
