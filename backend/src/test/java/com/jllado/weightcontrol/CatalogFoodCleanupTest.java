package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CatalogFoodCleanupTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("food_cleanup");
    @Test void cleanupPreservesNutrientsHistoryAndRetiredNames() throws Exception {
        flyway("58").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id,email,typical_calories_saturday,typical_calories_sunday,typical_calories_monday,typical_calories_tuesday,typical_calories_wednesday,typical_calories_thursday,typical_calories_friday) VALUES (800,'cleanup@example.com',2500,2500,2500,2500,2500,2500,2500),(801,'other-cleanup@example.com',2500,2500,2500,2500,2500,2500,2500)");
            statement.executeUpdate("""
                INSERT INTO catalog_foods (id,user_id,name,normalized_name,quantity,unit,calories,reference_quantity,reference_calories)
                VALUES (800,800,'Oats','oats',90,'GRAM',309,60,206),
                    (801,800,'Copos de Avena','copos de avena',1,'SERVING',206,1,206),
                    (802,800,'Uvas, 150 g','uvas, 150 g',1,'SERVING',101,1,101),
                    (803,801,'Copos de Avena','copos de avena',1,'SERVING',200,1,200)
                """);
            statement.executeUpdate("""
                CREATE TEMPORARY TABLE food_cleanup AS
                SELECT id,user_id,name old_name,name new_name,quantity old_quantity,quantity new_quantity,
                    unit old_unit,unit new_unit,reference_quantity old_reference_quantity,reference_quantity new_reference_quantity,
                    FALSE remove_food FROM catalog_foods WHERE id IN (801,802)
                """);
            statement.executeUpdate("UPDATE food_cleanup SET remove_food=TRUE WHERE id=801");
            statement.executeUpdate("UPDATE food_cleanup SET new_name='Grapes',new_quantity=150,new_unit='GRAM',new_reference_quantity=150 WHERE id=802");
            statement.executeUpdate("INSERT INTO meals (id,user_id,meal_date,meal_type,meal_sequence,calories,created_at,updated_at) VALUES (800,800,'2026-08-10','SNACK',1,206,NOW(),NOW())");
            statement.executeUpdate("INSERT INTO meal_dishes (meal_id,position,name,calories,quantity,unit,reference_quantity,reference_calories) VALUES (800,1,'Copos de Avena',206,1,'SERVING',1,206)");
            statement.executeUpdate("INSERT INTO dish_recipes (id,user_id,name,normalized_name,servings) VALUES (800,800,'Breakfast','breakfast',1)");
            statement.executeUpdate("INSERT INTO recipe_ingredients (recipe_id,position,name,quantity,unit,calories,reference_quantity,reference_calories) VALUES (800,1,'Copos de Avena',1,'SERVING',206,1,206)");
            var sql = java.nio.file.Files.readString(java.nio.file.Path.of("../scripts/sql/food-cleanup.sql"));
            sql = sql.replaceAll("(?m)^--.*$", "");
            for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
            try (var rows = statement.executeQuery("SELECT name,calories,reference_calories,quantity,reference_quantity FROM catalog_foods WHERE id=802")) {
                assertTrue(rows.next()); assertEquals("Grapes",rows.getString(1)); assertEquals(101,rows.getInt(2)); assertEquals(101,rows.getInt(3));
                assertEquals(150,rows.getInt(4)); assertEquals(150,rows.getInt(5));
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM catalog_foods WHERE user_id=800 AND deleted=FALSE")) {
                rows.next(); assertEquals(2,rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM catalog_foods WHERE user_id=800 AND deleted=TRUE AND normalized_name IN ('copos de avena','uvas, 150 g')")) {
                rows.next(); assertEquals(2,rows.getInt(1));
            }
            for (String table : new String[] {"meal_dishes", "recipe_ingredients"}) {
                try (var rows = statement.executeQuery("SELECT name,calories,quantity,unit,reference_quantity FROM " + table)) {
                    assertTrue(rows.next()); assertEquals("Copos de Avena",rows.getString(1)); assertEquals(206,rows.getInt(2));
                    assertEquals(1,rows.getInt(3)); assertEquals("SERVING",rows.getString(4)); assertEquals(1,rows.getInt(5)); assertFalse(rows.next());
                }
            }
            try (var rows = statement.executeQuery("SELECT deleted FROM catalog_foods WHERE id=803")) { rows.next(); assertFalse(rows.getBoolean(1)); }
        }
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
