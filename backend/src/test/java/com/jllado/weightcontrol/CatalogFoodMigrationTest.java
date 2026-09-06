package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CatalogFoodMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("food_migration");
    @Test void importsLatestUniqueFoodsPerUserWithoutChangingHistory() throws Exception {
        flyway("57").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                VALUES ('one@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500), ('two@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                """);
            statement.executeUpdate("""
                INSERT INTO meals (id, user_id, meal_date, meal_type, meal_sequence, calories, created_at, updated_at)
                VALUES (1, 1, '2026-08-10', 'SNACK', 1, 100, now(), now()), (2, 1, '2026-08-11', 'SNACK', 1, 200, now(), now()),
                    (3, 1, '2026-08-11', 'SNACK', 2, 300, now(), now()), (4, 1, '2026-08-09', 'SNACK', 1, 400, now(), now()),
                    (5, 2, '2026-08-12', 'SNACK', 1, 500, now(), now())
                """);
            statement.executeUpdate("""
                INSERT INTO meal_dishes (meal_id, position, name, calories, quantity, unit, reference_quantity, reference_calories)
                VALUES (1, 1, ' oats ', 100, 1, 'SERVING', 1, 100), (2, 1, 'OATS', 200, 1, 'SERVING', 1, 200),
                    (3, 1, 'Oats', 300, 1, 'SERVING', 1, 300), (3, 2, ' Oats ', 206, 60, 'GRAM', 60, 206),
                    (4, 1, 'oats', 400, 1, 'SERVING', 1, 400), (5, 1, 'Oats', 500, 1, 'SERVING', 1, 500),
                    (1, 2, 'Cafe', 10, 1, 'UNIT', 1, 10), (1, 3, 'Café', 20, 1, 'UNIT', 1, 20)
                """);
        }
        flyway("58").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT * FROM catalog_foods WHERE normalized_name = 'oats' ORDER BY user_id")) {
                assertTrue(result.next()); assertEquals("Oats", result.getString("name")); assertEquals(206, result.getInt("calories"));
                assertEquals(60, result.getInt("reference_quantity")); assertEquals("GRAM", result.getString("unit"));
                assertNull(result.getBigDecimal("protein_grams")); assertFalse(result.getBoolean("deleted"));
                assertTrue(result.next()); assertEquals(500, result.getInt("calories")); assertFalse(result.next());
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM catalog_foods")) { result.next(); assertEquals(4, result.getInt(1)); }
            try (var result = statement.executeQuery("SELECT count(*), sum(calories) FROM meal_dishes")) { result.next(); assertEquals(8, result.getInt(1)); assertEquals(1736, result.getInt(2)); }
            try (var result = statement.executeQuery("SELECT sum(calories) FROM meals")) { result.next(); assertEquals(1500, result.getInt(1)); }
        }
    }
    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
