package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WalkingMigrationTest {
    @Container static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("walking_migration");
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeEach void prepare() { flyway("121").clean(); flyway("121").migrate(); }

    @Test void addsDistinctCardioIdentitiesAndPreservesGenericWalking() throws Exception {
        try (var connection = DATABASE.createConnection("")) {
            var walking = snapshot(connection, "select * from exercises where built_in_image_key='walking'");
            flyway("123").migrate();
            assertEquals(walking, snapshot(connection, "select * from exercises where built_in_image_key='walking'"));
            assertEquals(List.of(List.of("Indoor walking", "CARDIO", "TRAINING", "SPEED_KPH", "indoor-walking"), List.of("Outdoor walking", "CARDIO", "TRAINING", "SPEED_KPH", "outdoor-walking")), snapshot(connection, "select name,tracking_mode,exercise_type,cardio_metric,built_in_image_key from exercises where built_in_image_key in ('indoor-walking','outdoor-walking') order by name"));
        }
        for (String key : List.of("indoor-walking", "outdoor-walking")) {
            try (var image = getClass().getResourceAsStream("/exercise-images/" + key + ".jpg")) {
                assertNotNull(image);
                var picture = ImageIO.read(image);
                assertNotNull(picture);
                assertTrue(picture.getWidth() >= 512 && picture.getHeight() >= 512);
            }
        }
        assertEquals(0, flyway("123").migrate().migrationsExecuted);
    }

    @Test void classifiesOnlyOwnedGenericLinesAndPreservesMeasurementsSnapshotsArchivesAndCurrentEdits() throws Exception {
        try (var connection = DATABASE.createConnection("")) {
            owners(connection);
            long walking = exercise(connection, "walking");
            for (int day = 2; day <= 8; day++) workout(connection, day, 1, "2026-11-0" + day, walking, 2410);
            workout(connection, 20, 2, "2026-11-08", walking, 2500);
            execute(connection, "insert into exercises (id,name,description,tracking_mode,exercise_type,cardio_metric,custom_image_path) values (900,'My outdoor walking','Custom','CARDIO','TRAINING','SPEED_KPH','custom/walking.jpg')");
            workout(connection, 21, 1, "2026-11-08", 900, 2500);
            String days = days(walking);
            plan(connection, 1, 1, days, false);
            plan(connection, 2, 1, days, true);
            plan(connection, 3, 2, days, false);
            var workouts = snapshot(connection, "select * from workouts order by id");
            var segments = snapshot(connection, "select * from workout_segments order by id");
            var lineMetadata = snapshot(connection, "select id,workout_id,position,created_at,updated_at,calories,average_heart_rate,exercise_duration_seconds,stretching_unit,superset_group_id from workout_lines order by id");
            var untouchedPlans = snapshot(connection, "select * from workout_plans where id in (2,3) order by id");
            flyway("123").migrate();
            assertEquals(List.of(List.of("2","indoor-walking"),List.of("3","indoor-walking"),List.of("4","indoor-walking"),List.of("5","indoor-walking"),List.of("6","indoor-walking"),List.of("7","outdoor-walking"),List.of("8","outdoor-walking"),List.of("20","walking")), snapshot(connection, "select l.workout_id,e.built_in_image_key from workout_lines l join exercises e on e.id=l.exercise_id where l.workout_id <> 21 order by l.workout_id"));
            assertEquals(List.of(List.of("900")), snapshot(connection, "select exercise_id from workout_lines where workout_id=21"));
            assertEquals(workouts, snapshot(connection, "select * from workouts order by id"));
            assertEquals(segments, snapshot(connection, "select * from workout_segments order by id"));
            assertEquals(lineMetadata, snapshot(connection, "select id,workout_id,position,created_at,updated_at,calories,average_heart_rate,exercise_duration_seconds,stretching_unit,superset_group_id from workout_lines order by id"));
            assertEquals(untouchedPlans, snapshot(connection, "select * from workout_plans where id in (2,3) order by id"));
            JsonNode actual = JSON.readTree(snapshot(connection, "select days_json from workout_plans where id=1").getFirst().getFirst());
            JsonNode expected = JSON.readTree(days);
            for (int day = 0; day < 2; day++) {
                var line = (com.fasterxml.jackson.databind.node.ObjectNode) (day == 0 ? expected.get(day).get("lines").get(0) : expected.get(day).get("sessions").get(0).get("lines").get(0));
                String key = day == 0 ? "indoor-walking" : "outdoor-walking";
                var identity = snapshot(connection, "select id,name,description from exercises where built_in_image_key='" + key + "'").getFirst();
                line.set("exerciseId", JSON.readTree(identity.get(0))); line.put("exerciseName", identity.get(1)); line.put("exerciseDescription", identity.get(2));
            }
            assertEquals(expected, actual);
            assertNotEquals("old-token", snapshot(connection, "select update_token from workout_plans where id=1").getFirst().getFirst());
            try (var statement = connection.createStatement()) { assertEquals(0, statement.executeUpdate("update workout_plans set notes='stale overwrite' where id=1 and update_token='old-token'")); }
            var after = snapshot(connection, "select * from workout_plans order by id");
            assertEquals(0, flyway("123").migrate().migrationsExecuted);
            assertEquals(after, snapshot(connection, "select * from workout_plans order by id"));
        }
    }

    @Test void assignsOnlyTheVerifiedRoutineAndBackfillsPerWorkoutNonSkippedOutdoorEvidence() throws Exception {
        try (var connection = DATABASE.createConnection("")) {
            owners(connection);
            long walking = exercise(connection, "walking");
            routine(connection, 52, 1, "Long outside walk", "WEEKLY", "2026-11-06 23:00:00");
            routine(connection, 53, 1, "Long outside walk", "WEEKLY", "2026-11-06 23:00:00");
            routine(connection, 54, 2, "Long outside walk", "WEEKLY", "2026-11-06 23:00:00");
            execute(connection, "insert into routine_reminders (routine_id,reminder_day,reminder_time,reminder_snoozed_until) values (52,'SUNDAY','18:30','2026-11-08 18:00:00')");
            execute(connection, "insert into routine_checkins (routine_id,checked_at,manual_completion,created_at) values (52,'2026-11-08 10:00:00',true,'2026-11-08 10:00:00'),(52,'2026-11-20 12:00:00',true,'2026-11-20 12:00:00')");
            workout(connection, 1, 1, "2026-10-31", walking, 3000); // Before start.
            workout(connection, 2, 1, "2026-11-07", walking, 2400); // Exact boundary.
            workout(connection, 3, 1, "2026-11-07", walking, 1200); // Separate workouts do not combine.
            workout(connection, 4, 1, "2026-11-07", walking, 1300);
            workout(connection, 5, 1, "2026-11-07", walking, 1200);
            segment(connection, 50, 5, 1201, false); // Multiple segments in one workout do combine.
            workout(connection, 6, 1, "2026-11-08", walking, 2400);
            segment(connection, 60, 6, 600, true); // Skipped work cannot cross the boundary.
            workout(connection, 7, 1, "2026-11-08", walking, 2401); // Existing manual completion retained.
            workout(connection, 8, 1, "2026-11-09", walking, 3000); // Indoor after classification.
            workout(connection, 9, 2, "2026-11-08", walking, 3000);
            workout(connection, 10, 1, "2026-11-14", walking, 2401); // Following Saturday is a new week.
            var reminders = snapshot(connection, "select * from routine_reminders order by id");
            var manual = snapshot(connection, "select * from routine_checkins order by id");
            var otherRoutines = snapshot(connection, "select * from routines where id<>52 order by id");
            var routineMetadata = snapshot(connection, "select id,user_id,start_date,name,cadence,created_at,updated_at,personal_records_enabled from routines where id=52");
            flyway("123").migrate();
            assertEquals(List.of(List.of("OUTDOOR_WALK_OVER_40_MINUTES","1","2","2","2026-11-20 12:00:00.000")), snapshot(connection, "select automatic_trigger,automatic_completion_enabled,current_strike,best_strike,last_time_date from routines where id=52"));
            assertEquals(List.of(List.of("WORKOUT","5","2026-11-07"),List.of("WORKOUT","7","2026-11-08"),List.of("WORKOUT","10","2026-11-14")), snapshot(connection, "select source_kind,source_key,event_date from routine_automatic_evidence where routine_id=52 order by event_date,cast(source_key as unsigned)"));
            assertEquals(manual, snapshot(connection, "select * from routine_checkins where manual_completion=true order by id"));
            assertEquals(List.of(List.of("2026-11-06 23:00:00.000"),List.of("2026-11-13 23:00:00.000")), snapshot(connection, "select checked_at from routine_checkins where manual_completion=false order by checked_at"));
            assertEquals(reminders, snapshot(connection, "select * from routine_reminders order by id"));
            assertEquals(otherRoutines, snapshot(connection, "select * from routines where id<>52 order by id"));
            assertEquals(routineMetadata, snapshot(connection, "select id,user_id,start_date,name,cadence,created_at,updated_at,personal_records_enabled from routines where id=52"));
            assertEquals(List.of(List.of("0")), snapshot(connection, "select count(*) from in_app_notifications"));
            var after = snapshot(connection, "select * from routine_checkins order by id");
            assertEquals(0, flyway("123").migrate().migrationsExecuted);
            assertEquals(after, snapshot(connection, "select * from routine_checkins order by id"));
        }
    }

    @Test void preservesMatchingCustomCatalogContentAndDoesNotAssignItAsBuiltInWalking() throws Exception {
        try (var connection = DATABASE.createConnection("")) {
            owners(connection);
            execute(connection, "insert into exercises (name,description,tracking_mode,exercise_type,custom_image_path) values ('INDOOR WALKING','My indoor exercise','SECONDS','STRETCHING','custom/indoor.jpg'),('Outdoor walking','My outdoor exercise','SECONDS','STRETCHING','custom/outdoor.jpg')");
            var catalog = snapshot(connection, "select * from exercises order by id");
            long walking = exercise(connection, "walking");
            workout(connection, 1, 1, "2026-11-08", walking, 2500);
            plan(connection, 1, 1, days(walking), false);
            routine(connection, 52, 1, "Long outside walk", "WEEKLY", "2026-11-06 23:00:00");
            var lines = snapshot(connection, "select * from workout_lines");
            var plans = snapshot(connection, "select * from workout_plans");
            flyway("123").migrate();
            assertEquals(catalog, snapshot(connection, "select * from exercises order by id"));
            assertEquals(lines, snapshot(connection, "select * from workout_lines"));
            assertEquals(plans, snapshot(connection, "select * from workout_plans"));
            assertEquals(List.of(List.of("NONE","0")), snapshot(connection, "select automatic_trigger,automatic_completion_enabled from routines where id=52"));
        }
    }

    @Test void enablesVerifiedRoutineWithNoCheckinsOrQualifyingWalkWithoutInventingHistory() throws Exception {
        try (var connection = DATABASE.createConnection("")) {
            owners(connection);
            routine(connection, 52, 1, "Long outside walk", "WEEKLY", "2026-11-06 23:00:00");
            execute(connection, "update routines set current_strike=0,best_strike=0 where id=52");
            workout(connection, 1, 1, "2026-11-08", exercise(connection, "walking"), 2280);
            flyway("123").migrate();
            assertEquals(List.of(List.of("OUTDOOR_WALK_OVER_40_MINUTES", "1", "0", "0")), snapshot(connection, "select automatic_trigger,automatic_completion_enabled,current_strike,best_strike from routines where id=52"));
            assertNull(snapshot(connection, "select last_time_date from routines where id=52").getFirst().getFirst());
            assertEquals(List.of(List.of("0")), snapshot(connection, "select count(*) from routine_checkins where routine_id=52"));
            assertEquals(List.of(List.of("0")), snapshot(connection, "select count(*) from routine_automatic_evidence where routine_id=52"));
        }
    }

    @Test void leavesUnverifiedRoutineIdentityUnchanged() throws Exception {
        try (var connection = DATABASE.createConnection("")) {
            owners(connection);
            routine(connection, 52, 1, "long outside walk", "WEEKLY", "2026-11-06 23:00:00");
            var before = snapshot(connection, "select * from routines where id=52");
            flyway("123").migrate();
            assertEquals(before, snapshot(connection, "select * from routines where id=52"));
        }
    }

    private void owners(Connection connection) throws Exception {
        for (String email : List.of("jllado@gmail.com", "other@example.com")) execute(connection, "insert into users (email,typical_calories_saturday,typical_calories_sunday,typical_calories_monday,typical_calories_tuesday,typical_calories_wednesday,typical_calories_thursday,typical_calories_friday) values ('" + email + "',2500,2500,2500,2500,2500,2500,2500)");
    }

    private long exercise(Connection connection, String key) throws Exception { return Long.parseLong(snapshot(connection, "select id from exercises where built_in_image_key='" + key + "'").getFirst().getFirst()); }
    private void routine(Connection connection, long id, long owner, String name, String cadence, String start) throws Exception { execute(connection, "insert into routines (id,user_id,start_date,name,cadence,current_strike,best_strike,created_at,updated_at) values (" + id + "," + owner + ",'" + start + "','" + name + "','" + cadence + "',1,1,'2026-11-01','2026-11-02')"); }
    private void workout(Connection connection, long id, long owner, String date, long exercise, int seconds) throws Exception {
        execute(connection, "insert into workouts (id,user_id,workout_date,session_reference,note,start_time,end_time,duration_minutes,cardio_minutes,planned_session_name,planned_targets_json,created_at,updated_at) values (" + id + "," + owner + ",'" + date + "',uuid(),'Keep note','18:01','2026-11-08 19:01:00',60,60,'Original walk','[{\"exerciseName\":\"Walking\",\"segments\":[{\"durationSeconds\":9999}]}]','2026-11-01 08:00','2026-11-02 09:00')");
        execute(connection, "insert into workout_lines (id,workout_id,exercise_id,position,calories,average_heart_rate,exercise_duration_seconds,created_at,updated_at) values (" + id + "," + id + "," + exercise + ",0,157,112,6000,'2026-11-01 08:01','2026-11-02 09:01')");
        segment(connection, id, id, seconds, false);
    }
    private void segment(Connection connection, long id, long line, int seconds, boolean skipped) throws Exception { execute(connection, "insert into workout_segments (id,workout_line_id,position,duration_seconds,speed_kph,distance_km,incline_percent,skipped,created_at,updated_at) values (" + id + "," + line + "," + id + "," + seconds + ",4.75,2.35,3.5," + skipped + ",'2026-11-01 08:02','2026-11-02 09:02')"); }
    private String days(long walking) { return "[{\"day\":\"MONDAY\",\"rest\":false,\"note\":\"Legacy day\",\"unknownDay\":42,\"lines\":[" + target(walking) + "]},{\"day\":\"SUNDAY\",\"rest\":false,\"sessions\":[{\"name\":\"Sunday edited\",\"note\":\"Keep newest\",\"unknownSession\":true,\"lines\":[" + target(walking) + ",{\"exerciseId\":1,\"exerciseName\":\"Left side target\",\"segments\":[{\"durationSeconds\":53}],\"unknownSide\":\"left\"},{\"exerciseId\":2,\"exerciseName\":\"Right side target\",\"segments\":[{\"durationSeconds\":47}],\"unknownSide\":\"right\"}]}]}]"; }
    private String target(long walking) { return "{\"exerciseId\":" + walking + ",\"exerciseName\":\"Walking\",\"exerciseDescription\":\"Old description\",\"trackingMode\":\"CARDIO\",\"exerciseType\":\"TRAINING\",\"cardioMetric\":\"SPEED_KPH\",\"segments\":[{\"durationSeconds\":2700,\"speedKph\":4.25}],\"unknownTarget\":\"keep\"}"; }
    private void plan(Connection connection, long id, long owner, String days, boolean archived) throws Exception {
        try (var statement = connection.prepareStatement("insert into workout_plans (id,user_id,start_date,review_date,notes,days_json,created_at,updated_at,archived_at,update_token) values (?,?,'2026-11-01','2026-12-01','Keep notes',?,'2026-11-01','2026-11-02',?, 'old-token')")) {
            statement.setLong(1,id); statement.setLong(2,owner); statement.setString(3,days); statement.setString(4,archived ? "2026-11-03" : null); statement.executeUpdate();
        }
    }
    private void execute(Connection connection, String sql) throws Exception { try (var statement = connection.createStatement()) { statement.executeUpdate(sql); } }
    private List<List<String>> snapshot(Connection connection, String query) throws Exception {
        var result = new ArrayList<List<String>>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) { var row = new ArrayList<String>(); for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.add(rows.getString(i)); result.add(row); }
        }
        return result;
    }
    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword()).locations("classpath:db/migration").cleanDisabled(false).target(target).load(); }
}
