package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import com.jllado.weightcontrol.domain.User;
import java.time.DayOfWeek;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class WeeklyMeasurementReminderDaysMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weekly_reminder_days");

    @Test
    void existingAndNewAccountsKeepSaturdayWhileCustomTimesArePreserved() throws Exception {
        flyway("94").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday,
                    weight_reminder_time, blood_pressure_reminder_time)
                values ('existing@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500, '08:10:00', '09:20:00')
                """);
        }
        flyway("95").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                values ('new@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                """);
            try (var result = statement.executeQuery("select * from users order by id")) {
                assertTrue(result.next());
                assertEquals("SATURDAY", result.getString("weight_reminder_day"));
                assertEquals("SATURDAY", result.getString("blood_pressure_reminder_day"));
                assertEquals("08:10:00", result.getTime("weight_reminder_time").toString());
                assertEquals("09:20:00", result.getTime("blood_pressure_reminder_time").toString());
                assertTrue(result.next());
                assertEquals("SATURDAY", result.getString("weight_reminder_day"));
                assertEquals("SATURDAY", result.getString("blood_pressure_reminder_day"));
                assertFalse(result.next());
            }
            statement.executeUpdate("update users set weight_reminder_day = 'MONDAY', blood_pressure_reminder_day = 'FRIDAY' where email = 'existing@example.com'");
            try (var result = statement.executeQuery("select weight_reminder_day, blood_pressure_reminder_day from users where email = 'existing@example.com'")) {
                assertTrue(result.next());
                assertEquals("MONDAY", result.getString(1));
                assertEquals("FRIDAY", result.getString(2));
            }
        }
        assertEquals(DayOfWeek.SATURDAY, new User().getWeightReminderDay());
        assertEquals(DayOfWeek.SATURDAY, new User().getBloodPressureReminderDay());
        assertEquals(0, flyway("95").migrate().migrationsExecuted);
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").target(target).load();
    }
}
