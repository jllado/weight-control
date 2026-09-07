package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DecisionReasonMigrationTest {
    @Container private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("decision_reason");

    @Test void preservesHistoricalOutcomesAndStoresOptionalReasons() throws Exception {
        flyway("58").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                    typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                VALUES ('one@example.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)
                """);
            statement.executeUpdate("INSERT INTO decision_outcomes (user_id, outcome_date, outcome) VALUES (1, '2026-08-11', 'WIN'), (1, '2026-08-11', 'MISS')");
        }
        flyway("59").migrate();
        try (var connection = DATABASE.createConnection(""); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT * FROM decision_outcomes ORDER BY id")) {
                assertTrue(result.next()); assertEquals("WIN", result.getString("outcome")); assertNull(result.getString("reason"));
                assertEquals("2026-08-11", result.getString("outcome_date")); assertEquals(1, result.getLong("user_id"));
                assertTrue(result.next()); assertEquals("MISS", result.getString("outcome")); assertNull(result.getString("reason")); assertFalse(result.next());
            }
            statement.executeUpdate("UPDATE decision_outcomes SET reason = 'Walked after lunch' WHERE id = 1");
            try (var result = statement.executeQuery("SELECT reason FROM decision_outcomes WHERE id = 1")) {
                assertTrue(result.next()); assertEquals("Walked after lunch", result.getString(1));
            }
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()).locations("classpath:db/migration").target(target).load();
    }
}
