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
class StandingAndTableStretchingMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("standing_table_stretching");

    @BeforeEach void preparePreviousCatalog() { flyway("73").clean(); flyway("73").migrate(); }

    @Test void expandsToThirtyFourTimedStretchesWithReadablePictures() throws Exception {
        flyway("74").migrate();
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
                assertEquals(34, count);
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM exercises WHERE exercise_type <> 'STRETCHING'")) { assertTrue(rows.next()); assertEquals(31, rows.getInt(1)); }
        }
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT name FROM exercises WHERE built_in_image_key IN ('behind-the-back-chest-stretch', 'table-hamstring-stretch', 'table-supported-shoulder-stretch', 'standing-forward-fold')")) {
            var names = new java.util.HashSet<String>();
            while (rows.next()) names.add(rows.getString(1));
            assertEquals(java.util.Set.of("Behind-the-back chest stretch", "Table hamstring stretch", "Table-supported shoulder stretch", "Standing forward fold"), names);
        }
        assertEquals(0, flyway("74").migrate().migrationsExecuted);
    }

    @Test void preservesAnExistingMatchingExerciseAndItsCustomPicture() throws Exception {
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO exercises (name, description, tracking_mode, custom_image_path) VALUES ('TABLE HAMSTRING STRETCH', 'My own exercise', 'REPS', 'exercise-images/custom.jpg')");
            statement.executeUpdate("UPDATE exercises SET name = 'My calf stretch', description = 'My own instructions', custom_image_path = 'exercise-images/calf.jpg' WHERE name = 'Wall calf stretch'");
        }
        flyway("74").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT description, tracking_mode, exercise_type, built_in_image_key, custom_image_path FROM exercises WHERE lower(name) = 'table hamstring stretch'")) {
                assertTrue(rows.next()); assertEquals("My own exercise", rows.getString(1)); assertEquals("REPS", rows.getString(2)); assertEquals("TRAINING", rows.getString(3)); assertNull(rows.getString(4)); assertEquals("exercise-images/custom.jpg", rows.getString(5)); assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("SELECT description, built_in_image_key, custom_image_path FROM exercises WHERE name = 'My calf stretch'")) {
                assertTrue(rows.next()); assertEquals("My own instructions", rows.getString(1)); assertEquals("wall-calf-stretch", rows.getString(2)); assertEquals("exercise-images/calf.jpg", rows.getString(3));
            }
            try (var rows = statement.executeQuery("SELECT count(*) FROM exercises WHERE exercise_type = 'STRETCHING'")) { assertTrue(rows.next()); assertEquals(33, rows.getInt(1)); }
        }
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").cleanDisabled(false).target(target).load(); }
}
