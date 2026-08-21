package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MealMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_meal_migration");

    @Test
    void splitsHistoricalDailyCaloriesBetweenLunchAndDinner() throws Exception {
        flyway("25").migrate();
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
                 ) values ('meal-migration@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                 """);
             var calories = connection.prepareStatement("""
                 insert into calories (user_id, calorie_date, calories, created_at, updated_at)
                 values (1, ?, ?, ?, ?)
                 """)) {
            user.executeUpdate();
            insertCalories(calories, "2026-08-08", 2000, createdAt, updatedAt);
            insertCalories(calories, "2026-08-09", 2001, createdAt, updatedAt);
            insertCalories(calories, "2026-08-10", 0, createdAt, updatedAt);
        }

        flyway(null).migrate();

        List<MigratedMeal> meals = new ArrayList<>();
        try (var connection = DATABASE.createConnection("");
             var statement = connection.prepareStatement("""
                 select meal_date, meal_type, meal_sequence, meal_time, calories, protein_grams, carbohydrate_grams, fat_grams, notes, source, created_at, updated_at
                 from meals
                 order by meal_date, meal_type desc
                 """);
             var result = statement.executeQuery()) {
            while (result.next()) {
                assertNull(result.getBigDecimal("protein_grams"));
                assertNull(result.getBigDecimal("carbohydrate_grams"));
                assertNull(result.getBigDecimal("fat_grams"));
                assertNull(result.getTime("meal_time"));
                assertNull(result.getString("notes"));
                assertEquals("MANUAL", result.getString("source"));
                assertEquals(createdAt, result.getTimestamp("created_at"));
                assertEquals(updatedAt, result.getTimestamp("updated_at"));
                meals.add(new MigratedMeal(
                    result.getDate("meal_date").toString(),
                    result.getString("meal_type"),
                    result.getInt("meal_sequence"),
                    result.getInt("calories")
                ));
            }
        }

        assertEquals(List.of(
            new MigratedMeal("2026-08-08", "LUNCH", 1, 1000),
            new MigratedMeal("2026-08-08", "DINNER", 1, 1000),
            new MigratedMeal("2026-08-09", "LUNCH", 1, 1000),
            new MigratedMeal("2026-08-09", "DINNER", 1, 1001),
            new MigratedMeal("2026-08-10", "LUNCH", 1, 0),
            new MigratedMeal("2026-08-10", "DINNER", 1, 0)
        ), meals);

        try (var connection = DATABASE.createConnection("");
             var duplicate = connection.prepareStatement("""
                 insert into meals (user_id, meal_date, meal_type, meal_sequence, calories)
                 values (1, '2026-08-08', 'LUNCH', 1, 1000)
                 """)) {
            assertThrows(SQLException.class, duplicate::executeUpdate);
        }

        try (var connection = DATABASE.createConnection("");
             var valid = connection.prepareStatement("""
                 insert into fasting_periods (user_id, start_time, end_time, notes)
                 values (1, '2026-08-19 20:00:00', '2026-08-20 12:00:00', 'Overnight fast')
                 """);
             var invalid = connection.prepareStatement("""
                 insert into fasting_periods (user_id, start_time, end_time)
                 values (1, '2026-08-20 12:00:00', '2026-08-20 11:00:00')
                 """)) {
            assertEquals(1, valid.executeUpdate());
            assertThrows(SQLException.class, invalid::executeUpdate);
        }
    }

    private void insertCalories(java.sql.PreparedStatement statement, String date, int calories, Timestamp createdAt, Timestamp updatedAt) throws Exception {
        statement.setString(1, date);
        statement.setInt(2, calories);
        statement.setTimestamp(3, createdAt);
        statement.setTimestamp(4, updatedAt);
        statement.executeUpdate();
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

    private record MigratedMeal(String date, String type, int sequence, int calories) {
    }
}
