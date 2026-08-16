package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class LipidPanelMigrationTest {

    @Container
    private static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:11.8")
        .withDatabaseName("weight_control_lipid_panels");

    @Test
    void createsLipidPanelsAndBackfillsOwnerHistory() throws Exception {
        flyway(MigrationVersion.fromVersion("28")).migrate();
        try (var connection = DATABASE.createConnection("");
             var statement = connection.createStatement()) {
            statement.executeUpdate("insert into users (email, typical_calories_saturday, typical_calories_sunday, typical_calories_monday, typical_calories_tuesday, typical_calories_wednesday, typical_calories_thursday, typical_calories_friday) values ('jllado@gmail.com', 2500, 2500, 2500, 2500, 2500, 2500, 2500)");
        }

        flyway(null).migrate();

        List<LipidPanelRow> panels = new ArrayList<>();
        try (var connection = DATABASE.createConnection("");
             var statement = connection.prepareStatement("select panel_date, total_cholesterol, hdl_cholesterol, ldl_cholesterol, triglycerides from lipid_panels order by panel_date");
             var result = statement.executeQuery()) {
            while (result.next()) {
                panels.add(new LipidPanelRow(
                    result.getDate("panel_date").toLocalDate(),
                    result.getInt("total_cholesterol"),
                    result.getInt("hdl_cholesterol"),
                    result.getInt("ldl_cholesterol"),
                    result.getInt("triglycerides")
                ));
            }
        }

        assertEquals(List.of(
            new LipidPanelRow(LocalDate.of(2021, 9, 4), 234, 45, 168, 107),
            new LipidPanelRow(LocalDate.of(2021, 12, 4), 228, 45, 165, 90),
            new LipidPanelRow(LocalDate.of(2022, 5, 21), 246, 43, 171, 160),
            new LipidPanelRow(LocalDate.of(2022, 12, 17), 187, 36, 118, 164),
            new LipidPanelRow(LocalDate.of(2023, 4, 1), 231, 54, 154, 116),
            new LipidPanelRow(LocalDate.of(2023, 9, 26), 257, 44, 197, 79),
            new LipidPanelRow(LocalDate.of(2024, 5, 4), 231, 50, 165, 78),
            new LipidPanelRow(LocalDate.of(2025, 2, 25), 174, 39, 122, 65),
            new LipidPanelRow(LocalDate.of(2025, 7, 29), 192, 63, 114, 77),
            new LipidPanelRow(LocalDate.of(2025, 9, 15), 210, 60, 138, 65),
            new LipidPanelRow(LocalDate.of(2026, 2, 2), 211, 63, 133, 77)
        ), panels);
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private record LipidPanelRow(
        LocalDate date,
        int totalCholesterol,
        int hdlCholesterol,
        int ldlCholesterol,
        int triglycerides
    ) {
    }
}
