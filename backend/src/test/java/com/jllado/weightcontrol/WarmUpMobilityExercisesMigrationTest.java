package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WarmUpMobilityExercisesMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("warm_up_mobility_migration");

    private static final Map<String, String> TRACKING_MODES = Map.of(
        "Standing lunge hip-flexor stretch", "SECONDS",
        "Calf stretch on step", "SECONDS",
        "Floor sit-to-stand without hands", "REPS",
        "Resistance-band shoulder pass-through", "REPS"
    );

    @BeforeEach
    void preparePreviousCatalog() {
        flyway("91").clean();
        flyway("91").migrate();
    }

    @Test
    void addsIllustratedWarmUpsWithAppropriateTracking() throws Exception {
        flyway("92").migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            for (var entry : TRACKING_MODES.entrySet()) {
                try (var rows = statement.executeQuery("select tracking_mode, exercise_type, built_in_image_key, description from exercises where name = '" + entry.getKey() + "'")) {
                    assertTrue(rows.next(), entry.getKey());
                    assertEquals(entry.getValue(), rows.getString("tracking_mode"));
                    assertEquals("WARM_UP", rows.getString("exercise_type"));
                    assertNotNull(rows.getString("built_in_image_key"));
                    assertTrue(rows.getString("description").length() > 20);
                    try (var stream = getClass().getResourceAsStream("/exercise-images/" + rows.getString("built_in_image_key") + ".jpg")) {
                        assertNotNull(stream, entry.getKey());
                        var image = ImageIO.read(stream);
                        assertNotNull(image, entry.getKey());
                        assertTrue(image.getWidth() >= 512, entry.getKey());
                        assertTrue(image.getHeight() >= 512, entry.getKey());
                    }
                    assertFalse(rows.next());
                }
            }
        }

        assertEquals(0, flyway("92").migrate().migrationsExecuted);
    }

    @Test
    void preservesAnExistingMatchingExerciseAndItsCustomPicture() throws Exception {
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into exercises (name, description, tracking_mode, custom_image_path) values ('CALF STRETCH ON STEP', 'My own description', 'REPS', 'exercise-images/custom.jpg')");
        }

        flyway("92").migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement();
             var rows = statement.executeQuery("select description, tracking_mode, exercise_type, built_in_image_key, custom_image_path from exercises where lower(name) = 'calf stretch on step'")) {
            assertTrue(rows.next());
            assertEquals("My own description", rows.getString("description"));
            assertEquals("REPS", rows.getString("tracking_mode"));
            assertEquals("TRAINING", rows.getString("exercise_type"));
            assertNull(rows.getString("built_in_image_key"));
            assertEquals("exercise-images/custom.jpg", rows.getString("custom_image_path"));
            assertFalse(rows.next());
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").cleanDisabled(false).target(target).load();
    }
}
