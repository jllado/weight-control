package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DumbbellOverheadTricepsExtensionMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("overhead_triceps_extension");

    @BeforeEach void preparePreviousCatalog() { flyway("111").clean(); flyway("111").migrate(); }

    @Test void addsTheRepetitionBasedTrainingExerciseWithPrimaryTricepsAndReadablePicture() throws Exception {
        flyway("112").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT description, tracking_mode, exercise_type, built_in_image_key, primary_muscle_group FROM exercises WHERE name = 'Dumbbell overhead triceps extension'")) {
                assertTrue(rows.next());
                assertEquals("Stand upright and hold one dumbbell overhead with both hands. Bend your elbows to lower it behind your head, then extend your arms under control while keeping your upper arms still.", rows.getString(1));
                assertEquals("REPS", rows.getString(2));
                assertEquals("TRAINING", rows.getString(3));
                assertEquals("dumbbell-overhead-triceps-extension", rows.getString(4));
                assertEquals("TRICEPS", rows.getString(5));
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("SELECT a.role FROM exercise_muscle_group_assignments a JOIN exercises e ON e.id = a.exercise_id WHERE e.name = 'Dumbbell overhead triceps extension' AND a.muscle_group = 'TRICEPS'")) {
                assertTrue(rows.next()); assertEquals("PRIMARY", rows.getString(1)); assertFalse(rows.next());
            }
        }
        try (var stream = getClass().getResourceAsStream("/exercise-images/dumbbell-overhead-triceps-extension.jpg")) {
            assertNotNull(stream);
            BufferedImage picture = ImageIO.read(stream);
            assertNotNull(picture);
            assertTrue(picture.getWidth() >= 512);
            assertTrue(picture.getHeight() >= 512);
        }
        assertEquals(0, flyway("112").migrate().migrationsExecuted);
    }

    @Test void preservesAnExistingMatchingExerciseAndItsCustomPicture() throws Exception {
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO exercises (name, description, tracking_mode, exercise_type, custom_image_path, primary_muscle_group) VALUES ('DUMBBELL OVERHEAD TRICEPS EXTENSION', 'My own exercise', 'SECONDS', 'TRAINING', 'exercise-images/custom.jpg', 'TRICEPS')");
        }
        flyway("112").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT description, tracking_mode, exercise_type, built_in_image_key, custom_image_path, primary_muscle_group FROM exercises WHERE lower(name) = 'dumbbell overhead triceps extension'")) {
            assertTrue(rows.next());
            assertEquals("My own exercise", rows.getString(1));
            assertEquals("SECONDS", rows.getString(2));
            assertEquals("TRAINING", rows.getString(3));
            assertNull(rows.getString(4));
            assertEquals("exercise-images/custom.jpg", rows.getString(5));
            assertEquals("TRICEPS", rows.getString(6));
            assertFalse(rows.next());
        }
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").cleanDisabled(false).target(target).load(); }
}
