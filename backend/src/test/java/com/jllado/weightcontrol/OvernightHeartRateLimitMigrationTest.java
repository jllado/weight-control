package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OvernightHeartRateLimitMigrationTest {
    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("heart_rate_migration");

    @Test void assignsTheAcceptedOwnerLimitAndUsesOnlyEachOtherOwnersFirstThirtyValidNights() throws Exception {
        flyway("127").migrate();
        var firstDate = LocalDate.of(2025, 1, 1);
        try (var db = DATABASE.createConnection("");
             var user = db.prepareStatement("""
                 insert into users (id, email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday,
                     typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday)
                 values (?, ?, 2000, 2000, 2000, 2000, 2000, 2000, 2000)
                 """);
             var night = db.prepareStatement("insert into sleeps (user_id, sleep_date, average_heart_rate) values (?, ?, ?)")) {
            var emails = List.of("enough@example.com", "sparse@example.com", "jllado@gmail.com", "empty@example.com");
            for (int owner = 0; owner < emails.size(); owner++) {
                user.setLong(1, owner + 100);
                user.setString(2, emails.get(owner));
                user.executeUpdate();
                int coverage = switch (owner) { case 0, 2 -> 37; case 1 -> 31; default -> 0; };
                for (int i = 0; i < coverage; i++) {
                    night.setLong(1, owner + 100);
                    night.setObject(2, firstDate.plusDays(i));
                    // 30 valid rates after the two missing readings; later low readings must not shift the median.
                    BigDecimal bpm = i == 0 ? null : i == 1 ? BigDecimal.ZERO : i < 17 ? new BigDecimal("54.01")
                        : i < 32 ? new BigDecimal("57.02") : BigDecimal.TEN;
                    night.setBigDecimal(3, bpm);
                    night.executeUpdate();
                }
            }
            flyway("128").migrate();
            try (var statement = db.createStatement();
                 var rows = statement.executeQuery("select user_id, effective_from, bpm from overnight_heart_rate_limits order by user_id")) {
                assertTrue(rows.next());
                assertEquals(100, rows.getInt(1));
                assertEquals(firstDate.plusDays(31), rows.getObject(2, LocalDate.class));
                assertEquals(new BigDecimal("60.515"), rows.getBigDecimal(3));
                assertTrue(rows.next());
                assertEquals(102, rows.getInt(1));
                assertEquals(LocalDate.of(2026, 11, 21), rows.getObject(2, LocalDate.class));
                assertEquals(new BigDecimal("70.500"), rows.getBigDecimal(3));
                assertFalse(rows.next());
            }
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
            .locations("classpath:db/migration").target(target).load();
    }
}
