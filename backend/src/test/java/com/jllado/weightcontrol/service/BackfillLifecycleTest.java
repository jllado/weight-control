package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jllado.weightcontrol.WeightControlBackendApplication;
import com.jllado.weightcontrol.config.SchedulingConfiguration;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.testcontainers.containers.MariaDBContainer;

class BackfillLifecycleTest {
    @TempDir Path temporary;

    @Test
    void actualAdministrativeStartupAndProcessWriteOnlyMissingSummariesAndExitCleanly() throws Exception {
        try (var database = new MariaDBContainer<>("mariadb:11.8").withDatabaseName("backfill_lifecycle")) {
            database.start();
            var arguments = arguments(database);
            var application = new SpringApplication(WeightControlBackendApplication.class);
            try (var normal = application.run(arguments.toArray(String[]::new))) {
                assertTrue(normal.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
                assertNotNull(normal.getBean(SchedulingConfiguration.class));
                assertNotNull(normal.getBean(PersonalRecordStartupRebuild.class));
                assertTrue(normal.containsBean("coachAuthAlertWorker"));
                var owner = new User();
                owner.setEmail("backfill-owner@example.com");
                owner = normal.getBean(UserRepository.class).save(owner);
                var friday = LocalDate.now(DateTimes.USER_ZONE).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusDays(3);
                try (var connection = connect(database); var statement = connection.prepareStatement(
                    "INSERT INTO moods (user_id,mood_date,period,value,created_at,updated_at) VALUES (?,?,'MORNING',3,NOW(),NOW())")) {
                    statement.setLong(1, owner.getId());
                    statement.setObject(2, friday.minusDays(7));
                    statement.executeUpdate();
                }
            }
            var baseline = fingerprints(database);
            var dryArguments = new ArrayList<>(arguments);
            dryArguments.add("--spring.main.web-application-type=none");
            dryArguments.add("--spring.flyway.enabled=false");
            dryArguments.add("--app.weekly-summary.backfill.mode=dry-run");
            try (var administrative = new SpringApplication(WeightControlBackendApplication.class).run(dryArguments.toArray(String[]::new))) {
                assertFalse(administrative.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
                assertTrue(administrative.getBeansOfType(PersonalRecordStartupRebuild.class).isEmpty());
                assertFalse(administrative.containsBean("coachAuthAlertWorker"));
                assertFalse(administrative.containsBean("securityFilterChain"));
                assertNotNull(administrative.getBean(WeeklySummaryBackfillRunner.class));
            }
            assertEquals(baseline, fingerprints(database));
            assertEquals(0, summaryCount(database));
            var dryOutput = runProcess(dryArguments, "dry-run.log");
            assertTrue(dryOutput.contains("backfill-owner@example.com"));
            assertTrue(dryOutput.contains("dry-run: 2 created, 0 existing"), dryOutput);
            assertEquals(baseline, fingerprints(database));
            var applyArguments = new ArrayList<>(dryArguments);
            applyArguments.remove("--app.weekly-summary.backfill.mode=dry-run");
            applyArguments.add("--app.weekly-summary.backfill.mode=apply");
            var applyOutput = runProcess(applyArguments, "apply.log");
            assertTrue(applyOutput.contains("apply: 2 created, 0 existing"), applyOutput);
            assertEquals(2, summaryCount(database));
            assertEquals(baseline, fingerprints(database));
            var existing = summaryRows(database);
            var rerunOutput = runProcess(applyArguments, "rerun.log");
            assertTrue(rerunOutput.contains("apply: 0 created, 2 existing"), rerunOutput);
            assertEquals(existing, summaryRows(database));
            assertEquals(baseline, fingerprints(database));
        }
    }

    private List<String> arguments(MariaDBContainer<?> database) {
        return new ArrayList<>(List.of("--server.port=0", "--spring.datasource.url=" + database.getJdbcUrl(),
            "--spring.datasource.username=" + database.getUsername(), "--spring.datasource.password=" + database.getPassword(),
            "--app.auth.google-client-id=test-client-id", "--app.chat-gpt-actions.public-base-url=https://test.example",
            "--app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long",
            "--app.weekly-summary.owner-email=backfill-owner@example.com", "--app.storage.root=" + temporary.resolve("data")));
    }

    private String runProcess(List<String> arguments, String file) throws Exception {
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", System.getProperty("app.test.main-classpath"), WeightControlBackendApplication.class.getName()));
        command.addAll(arguments);
        var output = temporary.resolve(file);
        var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(90, TimeUnit.SECONDS), "Administrative process did not exit cleanly");
            var log = Files.readString(output);
            assertEquals(0, process.exitValue(), log);
            assertFalse(log.contains("Started Tomcat"), log);
            return log;
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
        }
    }

    private Connection connect(MariaDBContainer<?> database) throws Exception {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    private Map<String, List<String>> fingerprints(MariaDBContainer<?> database) throws Exception {
        var result = new TreeMap<String, List<String>>();
        try (var connection = connect(database); var tables = connection.createStatement().executeQuery("SHOW TABLES")) {
            while (tables.next()) {
                var table = tables.getString(1);
                if (!table.equals("weekly_summaries")) result.put(table, rows(connection, table));
            }
        }
        return result;
    }

    private List<String> rows(Connection connection, String table) throws Exception {
        var values = new ArrayList<String>();
        try (var rows = connection.createStatement().executeQuery("SELECT * FROM `" + table + "` ORDER BY 1")) {
            while (rows.next()) {
                var row = new ArrayList<String>();
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) row.add(rows.getString(column));
                values.add(row.toString());
            }
        }
        return values;
    }

    private int summaryCount(MariaDBContainer<?> database) throws Exception {
        return summaryRows(database).size();
    }

    private List<String> summaryRows(MariaDBContainer<?> database) throws Exception {
        try (var connection = connect(database)) { return rows(connection, "weekly_summaries"); }
    }
}
