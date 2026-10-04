package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NutrientMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("nutrients");

    @Test void backfillsAllSnapshotTypesWithoutChangingExistingValuesAndRejectsStaleMappings() throws Exception {
        flyway("98").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id,email,typical_calories_saturday,typical_calories_sunday,typical_calories_monday,typical_calories_tuesday,typical_calories_wednesday,typical_calories_thursday,typical_calories_friday) VALUES (800,'nutrients@example.com',2500,2500,2500,2500,2500,2500,2500),(801,'other-nutrients@example.com',2500,2500,2500,2500,2500,2500,2500)");
            statement.executeUpdate("INSERT INTO meals (id,user_id,meal_date,meal_type,meal_sequence,calories,created_at,updated_at) VALUES (800,800,'2026-08-10','SNACK',1,100,NOW(),NOW())");
            statement.executeUpdate("INSERT INTO dish_recipes (id,user_id,name,normalized_name,servings) VALUES (800,800,'Breakfast','breakfast',1)");
            statement.executeUpdate("INSERT INTO catalog_foods (id,user_id,name,normalized_name,quantity,unit,calories,reference_quantity,reference_calories) VALUES (800,800,'Oats','oats',50,'GRAM',100,100,200),(801,801,'Oats','oats',50,'GRAM',100,100,200)");
            statement.executeUpdate("INSERT INTO meal_dishes (id,meal_id,position,name,quantity,unit,calories,reference_quantity,reference_calories) VALUES (800,800,1,'Oats',50,'GRAM',100,100,200)");
            statement.executeUpdate("INSERT INTO recipe_ingredients (id,recipe_id,position,name,quantity,unit,calories,reference_quantity,reference_calories) VALUES (800,800,1,'Oats',50,'GRAM',100,100,200)");
        }
        flyway("99").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            mapping(connection);
            statement.executeUpdate("UPDATE nutrient_backfill SET user_id=801 WHERE kind='meal_dishes'");
            assertThrows(SQLException.class, () -> apply(connection));
            connection.rollback();
        }
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT vitamin_d_micrograms FROM catalog_foods WHERE id=800")) {
                assertTrue(rows.next()); assertNull(rows.getBigDecimal(1));
            }
            mapping(connection);
            apply(connection);
            for (var table : new String[]{"catalog_foods", "meal_dishes", "recipe_ingredients"}) {
                try (var rows = statement.executeQuery("SELECT * FROM " + table + " WHERE id=800")) {
                    assertTrue(rows.next());
                    assertEquals(new BigDecimal("1.01"), rows.getBigDecimal("vitamin_d_micrograms"));
                    assertEquals(new BigDecimal("50.01"), rows.getBigDecimal("omega3_milligrams"));
                    assertEquals(new BigDecimal("10.01"), rows.getBigDecimal("magnesium_milligrams"));
                    assertEquals(new BigDecimal("2.01"), rows.getBigDecimal("reference_vitamin_d_micrograms"));
                    assertEquals("Test composition", rows.getString("nutrient_source"));
                    assertTrue(rows.getBoolean("nutrients_estimated"));
                    assertEquals("Oats", rows.getString("name")); assertEquals(100, rows.getInt("calories"));
                    assertEquals(200, rows.getInt("reference_calories")); assertEquals(50, rows.getInt("quantity"));
                    assertEquals("GRAM", rows.getString("unit")); assertEquals(100, rows.getInt("reference_quantity"));
                    assertNull(rows.getBigDecimal("protein_grams"));
                }
            }
            try (var rows = statement.executeQuery("SELECT vitamin_d_micrograms FROM catalog_foods WHERE id=801")) { rows.next(); assertNull(rows.getBigDecimal(1)); }
        }
        try (var connection = DATABASE.createConnection("")) {
            mapping(connection);
            assertThrows(SQLException.class, () -> apply(connection));
            connection.rollback();
        }
    }

    private void mapping(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TEMPORARY TABLE nutrient_backfill AS
                SELECT CAST('catalog_foods' AS CHAR(30)) kind, id, user_id, name old_name, quantity old_quantity, unit old_unit,
                    reference_quantity old_reference_quantity, calories old_calories, reference_calories old_reference_calories,
                    2.01 reference_vitamin_d_micrograms, 100.01 reference_omega3_milligrams, 20.01 reference_magnesium_milligrams,
                    'Test composition' nutrient_source, TRUE nutrients_estimated FROM catalog_foods WHERE id=800
                """);
            statement.executeUpdate("INSERT INTO nutrient_backfill SELECT 'meal_dishes', id,user_id,old_name,old_quantity,old_unit,old_reference_quantity,old_calories,old_reference_calories,reference_vitamin_d_micrograms,reference_omega3_milligrams,reference_magnesium_milligrams,nutrient_source,nutrients_estimated FROM nutrient_backfill WHERE kind='catalog_foods'");
            statement.executeUpdate("INSERT INTO nutrient_backfill SELECT 'recipe_ingredients', id,user_id,old_name,old_quantity,old_unit,old_reference_quantity,old_calories,old_reference_calories,reference_vitamin_d_micrograms,reference_omega3_milligrams,reference_magnesium_milligrams,nutrient_source,nutrients_estimated FROM nutrient_backfill WHERE kind='catalog_foods'");
        }
    }
    private void apply(Connection connection) throws Exception {
        var sql = Files.readString(Path.of("../scripts/sql/food-nutrients.sql")).replaceAll("(?m)^--.*$", "");
        try (var statement = connection.createStatement()) { for (var command : sql.split(";")) if (!command.isBlank()) statement.execute(command); }
    }
    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
