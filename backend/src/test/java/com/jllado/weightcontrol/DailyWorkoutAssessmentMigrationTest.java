package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DailyWorkoutAssessmentMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("daily_assessments");

    @Test void preservesOnlyCompleteSingleSessionRatingsAndEnforcesOwnerDateUniqueness() throws Exception {
        flyway("74").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (id, email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values (9001, 'daily-one@example.com', 2000, 2000, 2000, 2000, 2000, 2000, 2000), (9002, 'daily-two@example.com', 2000, 2000, 2000, 2000, 2000, 2000, 2000)");
            statement.executeUpdate("insert into workouts (id, user_id, workout_date, session_reference) values (9001, 9001, '2026-08-20', UUID()), (9002, 9001, '2026-08-21', UUID()), (9003, 9001, '2026-08-21', UUID()), (9004, 9002, '2026-08-21', UUID())");
            for (int id : new int[] {9001, 9002, 9004}) statement.executeUpdate("insert into workout_assessments (workout_id, goal_alignment_score, estimated_training_demand_score, rationale, strength, improvement, next_workout_action, goal_snapshot, plan_updated_at) values (" + id + ", 8, 7, 'Rationale', 'Strength', 'Improvement', 'Next action', 'Goal', '2026-08-20 10:00:00')");
        }
        flyway("75").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("select user_id, workout_date, goal_alignment_score, rationale from workout_assessments order by user_id")) {
                assertTrue(rows.next()); assertEquals(9001, rows.getLong(1)); assertEquals("2026-08-20", rows.getString(2)); assertEquals(8, rows.getInt(3)); assertEquals("Rationale", rows.getString(4));
                assertTrue(rows.next()); assertEquals(9002, rows.getLong(1)); assertEquals("2026-08-21", rows.getString(2)); assertFalse(rows.next());
            }
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("insert into workout_assessments (user_id, workout_date, goal_alignment_score, estimated_training_demand_score, rationale, strength, improvement, next_workout_action, goal_snapshot, plan_updated_at) select user_id, workout_date, goal_alignment_score, estimated_training_demand_score, rationale, strength, improvement, next_workout_action, goal_snapshot, plan_updated_at from workout_assessments limit 1"));
            try (var rows = statement.executeQuery("select count(*) from workouts where id between 9001 and 9004")) { assertTrue(rows.next()); assertEquals(4, rows.getInt(1)); }
            statement.executeUpdate("delete from workouts where user_id = 9001");
            statement.executeUpdate("delete from users where id = 9001");
            try (var rows = statement.executeQuery("select count(*) from workout_assessments")) { assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); }
        }
        assertEquals(0, flyway("75").migrate().migrationsExecuted);
    }

    private Flyway flyway(String target) { return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load(); }
}
