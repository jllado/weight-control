package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class HalfKneelingSingleArmDumbbellPressMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("half_kneeling_dumbbell_press");

    @BeforeEach void preparePreviousCatalog() { flyway("79").clean(); flyway("79").migrate(); }

    @Test void addsTheRepTrackedExerciseWithReadablePicture() throws Exception {
        flyway("80").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT name, tracking_mode, exercise_type, built_in_image_key, description FROM exercises WHERE lower(name) = 'half-kneeling single-arm dumbbell press'")) {
            assertTrue(rows.next()); assertEquals("Half-kneeling single-arm dumbbell press", rows.getString(1)); assertEquals("REPS", rows.getString(2)); assertEquals("TRAINING", rows.getString(3)); assertEquals("half-kneeling-single-arm-dumbbell-press", rows.getString(4)); assertTrue(rows.getString(5).contains("repeat on the other side")); assertFalse(rows.next());
            try (var stream = getClass().getResourceAsStream("/exercise-images/half-kneeling-single-arm-dumbbell-press.jpg")) {
                assertNotNull(stream); var picture = ImageIO.read(stream); assertNotNull(picture); assertTrue(picture.getWidth() >= 512); assertTrue(picture.getHeight() >= 512);
            }
        }
        assertEquals(0, flyway("80").migrate().migrationsExecuted);
    }

    @Test void preservesAnExistingMatchingExerciseAndItsCustomPicture() throws Exception {
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO exercises (name, description, tracking_mode, custom_image_path) VALUES ('HALF-KNEELING SINGLE-ARM DUMBBELL PRESS', 'My own exercise', 'SECONDS', 'exercise-images/custom.jpg')");
        }
        flyway("80").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT description, tracking_mode, exercise_type, built_in_image_key, custom_image_path FROM exercises WHERE lower(name) = 'half-kneeling single-arm dumbbell press'")) {
            assertTrue(rows.next()); assertEquals("My own exercise", rows.getString(1)); assertEquals("SECONDS", rows.getString(2)); assertEquals("TRAINING", rows.getString(3)); assertNull(rows.getString(4)); assertEquals("exercise-images/custom.jpg", rows.getString(5)); assertFalse(rows.next());
        }
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").cleanDisabled(false).target(target).load(); }
}
