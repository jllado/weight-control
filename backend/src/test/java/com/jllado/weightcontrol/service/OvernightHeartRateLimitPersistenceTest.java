package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.AuthenticatedUser;
import com.jllado.weightcontrol.security.JwtSessionService;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
@AutoConfigureMockMvc
class OvernightHeartRateLimitPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("heart_rate_limits"); }
    }

    private static final String URL = "/api/sleeps/heart-rate-limit";
    private static final LocalDate FIRST = LocalDate.of(2025, 3, 1);
    @Autowired JdbcTemplate jdbc;
    @Autowired SleepService sleeps;
    @Autowired OvernightHeartRateLimitService limits;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired JwtSessionService sessions;

    @Test void qualifyingEditsInitializeOnceAndSettingsUpdatesOnlyTodayForThisOwner() throws Exception {
        var owner = user();
        var other = user();
        for (int i = 0; i < 29; i++) {
            jdbc.update("insert into sleeps (user_id,sleep_date,average_heart_rate) values (?,?,?)", owner.getId(), FIRST.plusDays(i), i < 15 ? 54.01 : 57.02);
        }
        var date = FIRST.plusDays(33);
        var source = sleeps.create(owner, request(date, "0"));
        assertNull(limits.current(owner).bpm());
        mvc.perform(put(URL).cookie(cookie(owner)).contentType("application/json").content("{\"bpm\":57.1}"))
            .andExpect(status().isBadRequest());

        sleeps.update(owner, source.getId(), request(date, "73.23"));
        var original = limits.current(owner);
        assertEquals(new BigDecimal("60.515"), original.bpm());
        assertEquals(date, original.effectiveFrom());
        sleeps.update(owner, source.getId(), request(date.minusDays(1), "54.01"));
        sleeps.delete(owner, source.getId());
        assertEquals(original, limits.current(owner));
        assertTrue(limits.history(other).isEmpty());

        mvc.perform(get(URL)).andExpect(status().isForbidden());
        mvc.perform(get(URL).cookie(cookie(other))).andExpect(status().isOk()).andExpect(jsonPath("$.bpm").isEmpty());
        for (String invalid : new String[]{"0", "-1", "50.1234", "10000", "null"}) {
            mvc.perform(put(URL).cookie(cookie(owner)).contentType("application/json").content("{\"bpm\":" + invalid + "}"))
                .andExpect(status().isBadRequest());
        }
        var today = LocalDate.now(DateTimes.USER_ZONE);
        for (String bpm : new String[]{"63.5", "62.125", "62.125"}) {
            mvc.perform(put(URL).cookie(cookie(owner)).contentType("application/json").content("{\"bpm\":" + bpm + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.effectiveFrom").value(today.toString()));
        }
        mvc.perform(get(URL).cookie(cookie(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.bpm").value(62.125));
        var history = limits.history(owner);
        assertEquals(2, history.size());
        assertEquals(original.bpm(), history.getFirst().bpm());
        assertEquals(date, history.getFirst().effectiveFrom());
        assertEquals(today, history.getLast().effectiveFrom());
        assertTrue(limits.history(other).isEmpty());
    }

    private Cookie cookie(User user) {
        return new Cookie("wc_session", sessions.createToken(new AuthenticatedUser(user.getId(), user.getEmail())));
    }

    private User user() {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        return users.save(user);
    }

    private SleepRequest request(LocalDate date, String bpm) {
        return new SleepRequest(date, date.minusDays(1).atTime(23, 0).atOffset(ZoneOffset.UTC),
            date.atTime(7, 0).atOffset(ZoneOffset.UTC), 25200, 5400, 7200, 12600, 3600, new BigDecimal(bpm), 35);
    }
}
