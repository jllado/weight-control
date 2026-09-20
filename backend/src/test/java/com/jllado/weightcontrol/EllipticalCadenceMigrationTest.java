package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class EllipticalCadenceMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_elliptical_cadence");

    @Test
    void movesEllipticalSpeedAndRecordsToCadence() throws Exception {
        flyway(MigrationVersion.fromVersion("78")).migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('elliptical@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into workouts (user_id, workout_date, session_reference) select id, '2026-09-20', 'elliptical-session' from users where email = 'elliptical@example.com'");
            statement.executeUpdate("insert into workout_lines (workout_id, exercise_id, position) select workouts.id, exercises.id, 0 from workouts join users on users.id = workouts.user_id cross join exercises where users.email = 'elliptical@example.com' and exercises.name = 'Elliptical'");
            statement.executeUpdate("insert into workout_segments (workout_line_id, position, duration_seconds, speed_kph) select workout_lines.id, 0, 600, 82 from workout_lines join workouts on workouts.id = workout_lines.workout_id join users on users.id = workouts.user_id where users.email = 'elliptical@example.com'");
            statement.executeUpdate("insert into personal_record_snapshots (user_id, series_key, domain, metric, direction, exercise_id, value, record_date, source_type, source_id, line_position, segment_position) select users.id, concat('CARDIO_SPEED:', exercises.id), 'WORKOUT', 'CARDIO_SPEED', 'MAXIMUM', exercises.id, 82, '2026-09-20', 'WORKOUT', workouts.id, 0, 0 from users join workouts on workouts.user_id = users.id cross join exercises where users.email = 'elliptical@example.com' and exercises.name = 'Elliptical'");
        }

        flyway(MigrationVersion.fromVersion("79")).migrate();

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement();
             var segment = statement.executeQuery("select segments.speed_kph, segments.cadence_rpm from workout_segments segments join workout_lines on workout_lines.id = segments.workout_line_id join workouts on workouts.id = workout_lines.workout_id join users on users.id = workouts.user_id where users.email = 'elliptical@example.com'");
             var record = statement.executeQuery("select snapshots.series_key, snapshots.metric, snapshots.value, snapshots.exercise_id from personal_record_snapshots snapshots join users on users.id = snapshots.user_id where users.email = 'elliptical@example.com'")) {
            segment.next();
            assertEquals(null, segment.getBigDecimal("speed_kph"));
            assertEquals(0, new BigDecimal("82.00").compareTo(segment.getBigDecimal("cadence_rpm")));
            record.next();
            assertEquals("CARDIO_CADENCE:" + record.getLong("exercise_id"), record.getString("series_key"));
            assertEquals("CARDIO_CADENCE", record.getString("metric"));
            assertEquals(0, new BigDecimal("82.00").compareTo(record.getBigDecimal("value")));
        }
    }

    private Flyway flyway(MigrationVersion target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).target(target).load();
    }
}
