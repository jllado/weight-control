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
class SupineKneesSideToSideMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("supine_knees_side_to_side");

    @BeforeEach void preparePreviousCatalog() { flyway("93").clean(); flyway("93").migrate(); }

    @Test void addsADistinctTimedStretchWithReadableSquarePicture() throws Exception {
        flyway("94").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("select tracking_mode, exercise_type, built_in_image_key from exercises where lower(name) = 'supine knees side to side'")) {
                assertTrue(rows.next());
                assertEquals("SECONDS", rows.getString("tracking_mode"));
                assertEquals("STRETCHING", rows.getString("exercise_type"));
                assertEquals("supine-knees-side-to-side", rows.getString("built_in_image_key"));
                try (var stream = getClass().getResourceAsStream("/exercise-images/" + rows.getString("built_in_image_key") + ".jpg")) {
                    assertNotNull(stream);
                    var picture = ImageIO.read(stream);
                    assertNotNull(picture);
                    assertTrue(picture.getWidth() >= 512);
                    assertEquals(picture.getWidth(), picture.getHeight());
                }
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("select built_in_image_key from exercises where name = 'Lying spinal twist'")) {
                assertTrue(rows.next());
                assertEquals("lying-spinal-twist", rows.getString(1));
                assertFalse(rows.next());
            }
        }
        assertEquals(0, flyway("94").migrate().migrationsExecuted);
    }

    @Test void preservesAMatchingCustomExerciseWithoutAddingADuplicate() throws Exception {
        long exerciseId;
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into exercises (name, description, tracking_mode, custom_image_path) values ('SUPINE KNEES SIDE TO SIDE', 'My own exercise', 'REPS', 'exercise-images/custom.jpg')");
            try (var rows = statement.executeQuery("select id from exercises where name = 'SUPINE KNEES SIDE TO SIDE'")) {
                assertTrue(rows.next()); exerciseId = rows.getLong(1);
            }
        }
        flyway("94").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement(); var rows = statement.executeQuery("select id, name, description, tracking_mode, exercise_type, built_in_image_key, custom_image_path from exercises where lower(name) = 'supine knees side to side'")) {
            assertTrue(rows.next());
            assertEquals(exerciseId, rows.getLong("id"));
            assertEquals("SUPINE KNEES SIDE TO SIDE", rows.getString("name"));
            assertEquals("My own exercise", rows.getString("description"));
            assertEquals("REPS", rows.getString("tracking_mode"));
            assertEquals("TRAINING", rows.getString("exercise_type"));
            assertNull(rows.getString("built_in_image_key"));
            assertEquals("exercise-images/custom.jpg", rows.getString("custom_image_path"));
            assertFalse(rows.next());
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").cleanDisabled(false).target(target).load();
    }
}
