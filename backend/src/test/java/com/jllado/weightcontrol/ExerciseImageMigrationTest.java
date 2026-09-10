package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.imageio.ImageIO;

@Testcontainers
class ExerciseImageMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("exercise_images");

    @Test void mapsEverySeedToAPackagedPictureAndPreservesCustomExercises() throws Exception {
        flyway("62").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO exercises (name, description, tracking_mode) VALUES ('Custom exercise', 'Keep this', 'REPS')");
        }
        flyway("63").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT built_in_image_key FROM exercises WHERE built_in_image_key IS NOT NULL")) {
                int count = 0;
                while (rows.next()) {
                    try (var stream = getClass().getResourceAsStream("/exercise-images/" + rows.getString(1) + ".jpg")) {
                        assertNotNull(stream, rows.getString(1)); assertNotNull(ImageIO.read(stream));
                    }
                    count++;
                }
                assertEquals(39, count);
            }
            statement.executeUpdate("UPDATE exercises SET name = 'Renamed push-up' WHERE name = 'Push-up'");
            try (var rows = statement.executeQuery("SELECT built_in_image_key FROM exercises WHERE name = 'Renamed push-up'")) {
                assertTrue(rows.next()); assertEquals("push-up", rows.getString(1));
            }
            try (var rows = statement.executeQuery("SELECT built_in_image_key, custom_image_path, description FROM exercises WHERE name = 'Custom exercise'")) {
                assertTrue(rows.next()); assertNull(rows.getString(1)); assertNull(rows.getString(2)); assertEquals("Keep this", rows.getString(3));
            }
        }
        assertEquals(0, flyway("63").migrate().migrationsExecuted);
    }
    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
