package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PersonalRecordMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_personal_records");

    @Test
    void backfillsCurrentBodyAndWorkoutRecords() throws Exception {
        flyway(MigrationVersion.fromVersion("34")).migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('records@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
            statement.executeUpdate("insert into weights (user_id, measured_at, weight, fat_percentage, fat, muscle, muscle_percentage, lost_weight, lost_fat, lost_muscle) values (1, '2026-08-01 08:00:00', 80, 20, 16, 64, 80, 0, 0, 0), (1, '2026-08-08 08:00:00', 79, 19, 15, 65, 82, -1, -1, 1), (1, '2026-08-15 08:00:00', 79, 18, 14, 65, 82, 0, -1, 0)");
            statement.executeUpdate("insert into workouts (user_id, workout_date) values (1, '2026-08-02'), (1, '2026-08-09')");
            statement.executeUpdate("insert into workout_lines (workout_id, exercise_id, position) select 1, id, 0 from exercises where name = 'Squat'");
            statement.executeUpdate("insert into workout_lines (workout_id, exercise_id, position) select 2, id, 0 from exercises where name = 'Squat'");
            statement.executeUpdate("insert into workout_lines (workout_id, exercise_id, position) select 2, id, 1 from exercises where name = 'Running'");
            statement.executeUpdate("insert into workout_segments (workout_line_id, position, repetitions, weight) values (1, 0, 10, null), (1, 1, 5, 20.004), (2, 0, 12, 0), (2, 1, 6, 20)");
            statement.executeUpdate("insert into workout_segments (workout_line_id, position, duration_seconds, speed_kph, distance_km, incline_percent, resistance_level) values (3, 0, 720, 9, 2.5, 2, 3)");
        }

        flyway(null).migrate();

        List<RecordRow> records = new ArrayList<>();
        try (var connection = DATABASE.createConnection("");
             var statement = connection.prepareStatement("select metric, load_kg, value, record_date, source_id from personal_record_snapshots order by metric, load_kg");
             var result = statement.executeQuery()) {
            while (result.next()) {
                records.add(new RecordRow(result.getString("metric"), result.getBigDecimal("load_kg"), result.getBigDecimal("value"), result.getDate("record_date").toString(), result.getLong("source_id")));
            }
        }

        assertRecord(records, "BODY_WEIGHT", null, "79.00", "2026-08-08", 2L);
        assertRecord(records, "BODY_FAT_MASS", null, "14.00", "2026-08-15", 3L);
        assertRecord(records, "BODY_FAT_PERCENTAGE", null, "18.00", "2026-08-15", 3L);
        assertRecord(records, "BODY_MUSCLE_MASS", null, "65.00", "2026-08-08", 2L);
        assertRecord(records, "BODY_MUSCLE_PERCENTAGE", null, "82.00", "2026-08-08", 2L);
        assertRecord(records, "WORKOUT_HEAVIEST_LOAD", null, "20.00", "2026-08-02", 1L);
        assertRecord(records, "WORKOUT_REPETITIONS", "0.00", "12.00", "2026-08-09", 2L);
        assertRecord(records, "WORKOUT_REPETITIONS", "20.00", "6.00", "2026-08-09", 2L);
        assertRecord(records, "CARDIO_DURATION", null, "720.00", "2026-08-09", 2L);
        assertRecord(records, "CARDIO_RESISTANCE", null, "3.00", "2026-08-09", 2L);
        assertEquals(13, records.size());
    }

    private void assertRecord(List<RecordRow> records, String metric, String load, String value, String date, Long sourceId) {
        RecordRow record = records.stream()
            .filter(item -> item.metric().equals(metric))
            .filter(item -> load == null ? item.load() == null : item.load().compareTo(new BigDecimal(load)) == 0)
            .findFirst().orElseThrow();
        assertEquals(0, record.value().compareTo(new BigDecimal(value)));
        assertEquals(date, record.date());
        assertEquals(sourceId, record.sourceId());
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private record RecordRow(String metric, BigDecimal load, BigDecimal value, String date, Long sourceId) {
    }
}
