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
class SingleLegRecliningHeroPoseMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("reclining_hero_pose");

    @BeforeEach void preparePreviousCatalog() { flyway("77").clean(); flyway("77").migrate(); }

    @Test void addsTheTimedStretchWithReadablePicture() throws Exception {
        flyway("78").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT name, tracking_mode, built_in_image_key, description FROM exercises WHERE exercise_type = 'STRETCHING'")) {
                int count = 0;
                while (rows.next()) {
                    assertEquals("SECONDS", rows.getString(2), rows.getString(1));
                    assertNotNull(rows.getString(3)); assertTrue(rows.getString(4).contains("Record one hold per set"));
                    try (var stream = getClass().getResourceAsStream("/exercise-images/" + rows.getString(3) + ".jpg")) {
                        assertNotNull(stream, rows.getString(1)); var picture = ImageIO.read(stream); assertNotNull(picture); assertTrue(picture.getWidth() >= 512); assertTrue(picture.getHeight() >= 512);
                    }
                    count++;
                }
                assertEquals(35, count);
            }
            try (var rows = statement.executeQuery("SELECT name, tracking_mode, exercise_type, built_in_image_key, description FROM exercises WHERE lower(name) = 'single-leg reclining hero pose'")) {
                assertTrue(rows.next()); assertEquals("Single-leg reclining hero pose", rows.getString(1)); assertEquals("SECONDS", rows.getString(2)); assertEquals("STRETCHING", rows.getString(3)); assertEquals("single-leg-reclining-hero-pose", rows.getString(4)); assertTrue(rows.getString(5).contains("Gradually recline")); assertFalse(rows.next());
            }
        }
        assertEquals(0, flyway("78").migrate().migrationsExecuted);
    }

    @Test void preservesAnExistingMatchingExerciseAndItsCustomPicture() throws Exception {
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO exercises (name, description, tracking_mode, custom_image_path) VALUES ('SINGLE-LEG RECLINING HERO POSE', 'My own exercise', 'REPS', 'exercise-images/custom.jpg')");
        }
        flyway("78").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT description, tracking_mode, exercise_type, built_in_image_key, custom_image_path FROM exercises WHERE lower(name) = 'single-leg reclining hero pose'")) {
            assertTrue(rows.next()); assertEquals("My own exercise", rows.getString(1)); assertEquals("REPS", rows.getString(2)); assertEquals("TRAINING", rows.getString(3)); assertNull(rows.getString(4)); assertEquals("exercise-images/custom.jpg", rows.getString(5)); assertFalse(rows.next());
        }
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").cleanDisabled(false).target(target).load(); }
}
