package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BandPullApartStrengthMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("band_pull_apart_strength_migration");

    @Test
    void classifiesExistingBandPullApartAsStrengthAndPreservesItsTrackingAndPicture() throws Exception {
        flyway("89").migrate();
        flyway("90").migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement();
             var rows = statement.executeQuery("select description, tracking_mode, exercise_type, built_in_image_key, custom_image_path from exercises where name = 'Band pull-aparts'")) {
            assertTrue(rows.next());
            assertEquals("Band pull-aparts strengthen the rear shoulders and upper back.", rows.getString("description"));
            assertEquals("REPS", rows.getString("tracking_mode"));
            assertEquals("TRAINING", rows.getString("exercise_type"));
            assertEquals("band-pull-aparts", rows.getString("built_in_image_key"));
            assertNull(rows.getString("custom_image_path"));
            assertFalse(rows.next());
        }

        assertEquals(0, flyway("90").migrate().migrationsExecuted);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").cleanDisabled(false).target(target).load();
    }
}
