package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.api.dto.SleepDtos.SleepResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-30-bytes-long"})
class SleepOffsetPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false) static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("sleep_offsets"); }
    }
    @Autowired SleepService sleeps;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test void reloadRetainsTravelClocksAndActualInstants() {
        var owner = user();
        var start = OffsetDateTime.parse("2025-10-10T23:10:01-05:00");
        var end = OffsetDateTime.parse("2025-10-11T09:08:01-05:00");
        var saved = sleeps.create(owner, request(LocalDate.of(2025, 10, 11), start, end));
        var loaded = sleeps.requireOwned(owner, saved.getId());
        assertEquals(start.toInstant(), loaded.getBedtimeStart().toInstant());
        assertEquals(end.toInstant(), loaded.getBedtimeEnd().toInstant());
        assertEquals(start, loaded.getBedtimeStart());
        assertEquals(end, loaded.getBedtimeEnd());
        assertEquals("10/10/2025 23:10", SleepResponse.from(loaded).bedtimeStartFormat());
        assertEquals("11/10/2025 09:08", SleepResponse.from(loaded).bedtimeEndFormat());
        assertEquals(35880, Duration.between(loaded.getBedtimeStart(), loaded.getBedtimeEnd()).getSeconds());
        sleeps.update(owner, saved.getId(), request(loaded.getSleepDate(), loaded.getBedtimeStart(), loaded.getBedtimeEnd()));
        assertEquals(start, sleeps.requireOwned(owner, saved.getId()).getBedtimeStart());
    }

    @Test void reloadRetainsDifferentOffsetsAcrossDaylightSaving() {
        var owner = user();
        var start = OffsetDateTime.parse("2025-10-25T23:00:00+02:00");
        var end = OffsetDateTime.parse("2025-10-26T07:00:00+01:00");
        var saved = sleeps.create(owner, request(LocalDate.of(2025, 10, 26), start, end));
        var loaded = sleeps.requireOwned(owner, saved.getId());
        assertEquals(start, loaded.getBedtimeStart());
        assertEquals(end, loaded.getBedtimeEnd());
        assertEquals(9, Duration.between(loaded.getBedtimeStart(), loaded.getBedtimeEnd()).toHours());
    }

    @Test void legacyRowsKeepMadridSeasonalClocksAndMissingEndpoints() {
        var owner = user();
        var start = OffsetDateTime.parse("2025-10-25T23:00:00+02:00");
        var end = OffsetDateTime.parse("2025-10-26T07:00:00+01:00");
        var saved = sleeps.create(owner, request(LocalDate.of(2025, 10, 26), start, end));
        jdbc.update("update sleeps set bedtime_start_offset_seconds=null, bedtime_end_offset_seconds=null where id=?", saved.getId());
        var loaded = sleeps.requireOwned(owner, saved.getId());
        assertEquals(start, loaded.getBedtimeStart());
        assertEquals(end, loaded.getBedtimeEnd());
        var response = SleepResponse.from(loaded);
        assertEquals(7200, response.bedtimeStartOffsetSeconds());
        assertEquals(3600, response.bedtimeEndOffsetSeconds());
        jdbc.update("update sleeps set bedtime_start=null where id=?", saved.getId());
        response = SleepResponse.from(sleeps.requireOwned(owner, saved.getId()));
        assertNull(response.bedtimeStart());
        assertNull(response.bedtimeStartOffsetSeconds());
        assertEquals(end, response.bedtimeEnd());
    }

    @Test void repairingOffsetsKeepsInstantsMeasurementsAndOtherOwnersUnchanged() {
        var owner = user(); var other = user();
        var start = OffsetDateTime.parse("2025-10-11T04:10:01Z");
        var end = OffsetDateTime.parse("2025-10-11T14:08:01Z");
        var date = LocalDate.of(2025, 10, 11);
        var saved = sleeps.create(owner, request(date, start, end));
        var untouched = sleeps.create(other, request(date, start, end));
        var offset = java.time.ZoneOffset.ofHours(-5);
        sleeps.update(owner, saved.getId(), request(date, start.withOffsetSameInstant(offset), end.withOffsetSameInstant(offset)));
        var loaded = sleeps.requireOwned(owner, saved.getId());
        assertEquals(start.toInstant(), loaded.getBedtimeStart().toInstant());
        assertEquals(end.toInstant(), loaded.getBedtimeEnd().toInstant());
        assertEquals(28020, loaded.getTotalSleepDuration());
        assertEquals(7860, loaded.getAwakeTime());
        assertEquals(date, loaded.getSleepDate());
        assertEquals(offset, loaded.getBedtimeStart().getOffset());
        assertEquals(start, sleeps.requireOwned(other, untouched.getId()).getBedtimeStart());
        assertThrows(NotFoundException.class, () -> sleeps.update(other, saved.getId(), request(date, start, end)));
    }

    @Test void jsonWritesRetainOffsetsForAppAndCoach() throws Exception {
        String json = """
            {"sleepDate":"2025-10-11","bedtimeStart":"2025-10-10T23:10:01-05:00",
             "bedtimeEnd":"2025-10-11T09:08:01-05:00","totalSleepDuration":28020,
             "deepSleepDuration":4980,"remSleepDuration":6450,"lightSleepDuration":16590,
             "awakeTime":7860,"averageHeartRate":74.01,"averageHrv":13}
            """;
        var request = mapper.readValue(json, SleepRequest.class);
        assertEquals(java.time.ZoneOffset.ofHours(-5), request.bedtimeStart().getOffset());
        var owner = user();
        var saved = sleeps.create(owner, request);
        assertEquals(request.bedtimeStart(), sleeps.requireOwned(owner, saved.getId()).getBedtimeStart());
        var coach = mapper.readValue(json.replace("\"averageHrv\":13", "\"averageHrv\":13,\"confirmed\":true"), com.jllado.weightcontrol.api.dto.SleepDtos.CoachSleepRequest.class);
        assertEquals(request.bedtimeStart(), coach.bedtimeStart());
        assertEquals(request.bedtimeEnd(), coach.bedtimeEnd());
    }

    private User user() { var user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); return users.save(user); }
    private SleepRequest request(LocalDate date, OffsetDateTime start, OffsetDateTime end) {
        return new SleepRequest(date, start, end, 28020, 4980, 6450, 16590, 7860, BigDecimal.valueOf(74.01), 13);
    }
}
