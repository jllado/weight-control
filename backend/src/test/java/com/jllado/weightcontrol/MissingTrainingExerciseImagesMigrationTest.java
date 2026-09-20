package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MissingTrainingExerciseImagesMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("missing_training_images");
    private static final Map<String, String> PICTURES = Map.of(
        "Curl", "dumbbell-curl", "Dumbbell Side Bend", "dumbbell-side-bend", "Dumbbell walking lunges", "dumbbell-walking-lunges",
        "Hip thrust", "hip-thrust", "Lateral shoulder raise", "lateral-shoulder-raise", "Roman chair back extension", "roman-chair-back-extension",
        "Suspension chest press", "suspension-chest-press"
    );

    @BeforeEach void preparePreviousCatalog() { flyway("80").clean(); flyway("80").migrate(); }

    @Test void addsTheTrainingExercisesWithReadablePictures() throws Exception {
        flyway("82").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            for (var entry : PICTURES.entrySet()) {
                try (var rows = statement.executeQuery("SELECT tracking_mode, exercise_type, built_in_image_key FROM exercises WHERE name = '" + entry.getKey() + "'")) {
                    assertTrue(rows.next()); assertEquals("REPS", rows.getString(1)); assertEquals("TRAINING", rows.getString(2)); assertEquals(entry.getValue(), rows.getString(3)); assertFalse(rows.next());
                }
                try (var stream = getClass().getResourceAsStream("/exercise-images/" + entry.getValue() + ".jpg")) {
                    assertNotNull(stream); var picture = ImageIO.read(stream); assertNotNull(picture); assertTrue(picture.getWidth() >= 512); assertTrue(picture.getHeight() >= 512);
                }
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM exercises")) { assertTrue(rows.next()); assertEquals(74, rows.getInt(1)); }
        }
        assertEquals(0, flyway("82").migrate().migrationsExecuted);
    }

    @Test void mapsPicturelessProductionEntriesAndPreservesCustomPictures() throws Exception {
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO exercises (name, description, tracking_mode) VALUES ('CURL', 'Existing description', 'SECONDS')");
            statement.executeUpdate("INSERT INTO exercises (name, description, tracking_mode, custom_image_path) VALUES ('HIP THRUST', 'My own exercise', 'SECONDS', 'exercise-images/custom.jpg')");
        }
        flyway("82").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT description, tracking_mode, built_in_image_key, custom_image_path FROM exercises WHERE lower(name) = 'curl'")) {
                assertTrue(rows.next()); assertEquals("Existing description", rows.getString(1)); assertEquals("SECONDS", rows.getString(2)); assertEquals("dumbbell-curl", rows.getString(3)); assertNull(rows.getString(4)); assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("SELECT description, tracking_mode, built_in_image_key, custom_image_path FROM exercises WHERE lower(name) = 'hip thrust'")) {
                assertTrue(rows.next()); assertEquals("My own exercise", rows.getString(1)); assertEquals("SECONDS", rows.getString(2)); assertNull(rows.getString(3)); assertEquals("exercise-images/custom.jpg", rows.getString(4)); assertFalse(rows.next());
            }
        }
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").cleanDisabled(false).target(target).load(); }
}
