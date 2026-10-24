package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SeparateNutrientMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("separate_nutrients");

    @Test void additiveMigrationsLeaveLegacyValuesAndMetadataUntouched() throws Exception {
        flyway("111").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO users (id,email,typical_calories_saturday,typical_calories_sunday,typical_calories_monday,
                    typical_calories_tuesday,typical_calories_wednesday,typical_calories_thursday,typical_calories_friday,
                    nutrient_vitamin_d_target_micrograms,nutrient_omega3_target_milligrams,nutrient_magnesium_target_milligrams)
                VALUES (880,'separate-nutrients@example.com',2050,2050,2050,2050,2050,2050,2050,15,1200,350)
                """);
            statement.executeUpdate("INSERT INTO meals (id,user_id,meal_date,meal_type,meal_sequence,calories,created_at,updated_at) VALUES (880,880,'2026-10-24','LUNCH',1,100,'2026-10-24 10:00:00','2026-10-24 10:00:00')");
            statement.executeUpdate("INSERT INTO dish_recipes (id,user_id,name,normalized_name,servings) VALUES (880,880,'Oats','oats',1)");
            statement.executeUpdate("INSERT INTO catalog_foods (id,user_id,name,normalized_name,quantity,unit,calories,reference_quantity,reference_calories,vitamin_d_micrograms,omega3_milligrams,magnesium_milligrams,reference_omega3_milligrams,nutrient_source,nutrients_estimated) VALUES (880,880,'Oats','oats',50,'GRAM',100,100,200,1.25,100.25,25.50,200.50,'Legacy composition',TRUE)");
            statement.executeUpdate("INSERT INTO meal_dishes (id,meal_id,position,name,quantity,unit,calories,reference_quantity,reference_calories,vitamin_d_micrograms,omega3_milligrams,magnesium_milligrams,reference_omega3_milligrams,nutrient_source,nutrients_estimated) VALUES (880,880,1,'Oats',50,'GRAM',100,100,200,1.25,100.25,25.50,200.50,'Legacy composition',TRUE)");
            statement.executeUpdate("INSERT INTO recipe_ingredients (id,recipe_id,position,name,quantity,unit,calories,reference_quantity,reference_calories,vitamin_d_micrograms,omega3_milligrams,magnesium_milligrams,reference_omega3_milligrams,nutrient_source,nutrients_estimated) VALUES (880,880,1,'Oats',50,'GRAM',100,100,200,1.25,100.25,25.50,200.50,'Legacy composition',TRUE)");
        }

        flyway(null).migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT nutrient_vitamin_d_target_micrograms,nutrient_omega3_target_milligrams,nutrient_magnesium_target_milligrams,nutrient_epa_dha_target_milligrams,nutrient_ala_target_milligrams,nutrient_fiber_target_grams,macro_protein_target_grams,macro_fat_target_percentage FROM users WHERE id=880")) {
                assertTrue(rows.next());
                assertEquals(new BigDecimal("15.00"), rows.getBigDecimal(1));
                assertEquals(new BigDecimal("1200.00"), rows.getBigDecimal(2));
                assertEquals(new BigDecimal("350.00"), rows.getBigDecimal(3));
                for (int column = 4; column <= 8; column++) assertNull(rows.getBigDecimal(column));
            }
            for (var table : new String[]{"catalog_foods", "meal_dishes", "recipe_ingredients"}) {
                try (var rows = statement.executeQuery("SELECT vitamin_d_micrograms,omega3_milligrams,magnesium_milligrams,reference_omega3_milligrams,nutrient_source,nutrients_estimated,epa_dha_milligrams,reference_epa_dha_milligrams,epa_dha_source,epa_dha_estimated,ala_milligrams,reference_ala_milligrams,ala_source,ala_estimated,fiber_grams,reference_fiber_grams,fiber_source,fiber_estimated FROM " + table + " WHERE id=880")) {
                    assertTrue(rows.next());
                    assertEquals(new BigDecimal("1.25"), rows.getBigDecimal(1));
                    assertEquals(new BigDecimal("100.25"), rows.getBigDecimal(2));
                    assertEquals(new BigDecimal("25.50"), rows.getBigDecimal(3));
                    assertEquals(new BigDecimal("200.50"), rows.getBigDecimal(4));
                    assertEquals("Legacy composition", rows.getString(5));
                    assertTrue(rows.getBoolean(6));
                    for (int column = 7; column <= 18; column++) assertNull(rows.getObject(column));
                }
            }
            try (var rows = statement.executeQuery("SELECT created_at,updated_at FROM meals WHERE id=880")) {
                assertTrue(rows.next());
                assertEquals(rows.getTimestamp(1), rows.getTimestamp(2));
                assertEquals("2026-10-24 10:00:00.0", rows.getTimestamp(1).toString());
            }
        }
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
