package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InShapeBodyFatLimitMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("in_shape_migration");

    @Test
    void defaultsAndPersonalLimitsPersistPerAccountWithoutChangingHistory() throws Exception {
        flyway("107").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            insertUser(connection, "existing@example.com");
            statement.executeUpdate("update users set height_cm=180, weekly_average_calorie_maximum=2400, created_at='2020-01-01 00:00:00', updated_at='2021-01-01 00:00:00'");
            statement.executeUpdate("insert into weights (user_id, measured_at, weight, fat_percentage, fat, muscle, muscle_percentage, lost_weight, lost_fat, lost_muscle, created_at, updated_at) values (1, '2026-08-01 06:00:00', 80, 17, 13.6, 60, 75, 0, 0, 0, '2026-08-01 06:00:00', '2026-08-01 06:00:00')");
        }

        assertEquals(1, flyway("108").migrate().migrationsExecuted);

        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            insertUser(connection, "other@example.com");
            try (var rows = statement.executeQuery("select in_shape_body_fat_limit from users order by id")) {
                for (int i = 0; i < 2; i++) { assertTrue(rows.next()); assertEquals(new BigDecimal("17.00"), rows.getBigDecimal(1)); }
                assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("select height_cm, weekly_average_calorie_maximum, created_at, updated_at from users where id=1")) {
                assertTrue(rows.next()); assertEquals(180, rows.getInt(1)); assertEquals(2400, rows.getInt(2));
                assertEquals("2020-01-01 00:00:00.0", rows.getTimestamp(3).toString()); assertEquals("2021-01-01 00:00:00.0", rows.getTimestamp(4).toString());
            }
            statement.executeUpdate("update users set in_shape_body_fat_limit=18.25 where id=1");
        }
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("select in_shape_body_fat_limit from users order by id")) {
                assertTrue(rows.next()); assertEquals(new BigDecimal("18.25"), rows.getBigDecimal(1));
                assertTrue(rows.next()); assertEquals(new BigDecimal("17.00"), rows.getBigDecimal(1)); assertFalse(rows.next());
            }
            try (var rows = statement.executeQuery("select user_id, fat_percentage, measured_at, created_at, updated_at from weights")) {
                assertTrue(rows.next()); assertEquals(1, rows.getLong(1)); assertEquals(new BigDecimal("17.00"), rows.getBigDecimal(2));
                for (int i=3;i<=5;i++) assertEquals("2026-08-01 06:00:00.0", rows.getTimestamp(i).toString());
                assertFalse(rows.next());
            }
        }
        assertEquals(0, flyway("108").migrate().migrationsExecuted);
    }

    private void insertUser(Connection connection, String email) throws Exception {
        try (var statement = connection.prepareStatement("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values (?,2500,2500,2500,2500,2500,2500,2500)")) {
            statement.setString(1, email); statement.executeUpdate();
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load();
    }
}
