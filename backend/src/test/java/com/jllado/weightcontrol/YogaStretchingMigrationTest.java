package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.imageio.ImageIO;

@Testcontainers
class YogaStretchingMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("yoga_stretching");

    @BeforeEach void preparePreviousCatalog() { flyway("65").clean(); flyway("65").migrate(); }

    @Test void expandsToThirtyTimedStretchesWithReadablePictures() throws Exception {
        flyway("66").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT name, tracking_mode, default_warm_up, default_repetitions, built_in_image_key, description FROM exercises WHERE exercise_type = 'STRETCHING'")) {
                int count = 0;
                while (rows.next()) {
                    assertEquals("SECONDS", rows.getString(2), rows.getString(1)); assertFalse(rows.getBoolean(3)); assertNull(rows.getObject(4));
                    assertNotNull(rows.getString(5)); assertTrue(rows.getString(6).contains("Record one hold per set"));
                    try (var stream = getClass().getResourceAsStream("/exercise-images/" + rows.getString(5) + ".jpg")) {
                        assertNotNull(stream, rows.getString(1)); var picture = ImageIO.read(stream); assertNotNull(picture); assertTrue(picture.getWidth() >= 512); assertTrue(picture.getHeight() >= 512);
                    }
                    count++;
                }
                assertEquals(30, count);
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM exercises WHERE exercise_type <> 'STRETCHING'")) { assertTrue(rows.next()); assertEquals(31, rows.getInt(1)); }
        }
        assertEquals(0, flyway("66").migrate().migrationsExecuted);
    }

    @Test void preservesAnExistingMatchingExerciseAndItsCustomPicture() throws Exception {
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO exercises (name, description, tracking_mode, custom_image_path) VALUES ('LYING STRAIGHT-LEG HOLD', 'My own exercise', 'REPS', 'exercise-images/custom.jpg')");
            statement.executeUpdate("UPDATE exercises SET name = 'My calf stretch', description = 'My own instructions', custom_image_path = 'exercise-images/calf.jpg' WHERE name = 'Wall calf stretch'");
        }
        flyway("66").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT description, tracking_mode, exercise_type, built_in_image_key, custom_image_path FROM exercises WHERE lower(name) = 'lying straight-leg hold'")) {
                assertTrue(rows.next()); assertEquals("My own exercise", rows.getString(1)); assertEquals("REPS", rows.getString(2)); assertEquals("TRAINING", rows.getString(3)); assertNull(rows.getString(4)); assertEquals("exercise-images/custom.jpg", rows.getString(5)); assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("SELECT description, built_in_image_key, custom_image_path FROM exercises WHERE name = 'My calf stretch'")) {
                assertTrue(rows.next()); assertEquals("My own instructions", rows.getString(1)); assertEquals("wall-calf-stretch", rows.getString(2)); assertEquals("exercise-images/calf.jpg", rows.getString(3));
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM exercises WHERE exercise_type = 'STRETCHING'")) { assertTrue(rows.next()); assertEquals(29, rows.getInt(1)); }
        }
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").cleanDisabled(false).target(target).load(); }
}
