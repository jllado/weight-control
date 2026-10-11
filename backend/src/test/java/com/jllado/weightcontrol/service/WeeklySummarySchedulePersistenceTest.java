package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryScheduleRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {
    "app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long",
    "app.weekly-summary.enabled=true", "app.weekly-summary.owner-email=owner@example.com",
    "app.weekly-summary.scheduling-enabled=false"
})
class WeeklySummarySchedulePersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection
        MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("weekly_schedule"); }
    }

    @Autowired private UserRepository users;
    @Autowired private SavedWeeklySummaryRepository summaries;
    @Autowired private WeeklySummaryService service;
    @MockitoBean private WeeklySummaryMailSender mail;
    private User owner;
    private final ZonedDateTime due = ZonedDateTime.parse("2026-08-18T08:30:00+02:00[Europe/Madrid]");

    @BeforeEach
    void createOwner() {
        owner = new User();
        owner.setEmail("owner@example.com");
        owner = users.saveAndFlush(owner);
    }

    @AfterEach
    void removeTestRecords() {
        summaries.deleteAll();
        users.deleteAll();
    }

    @Test
    void defaultsAndChangedSchedulePersistWithoutSending() {
        var original = service.config(users.findById(owner.getId()).orElseThrow());
        assertEquals(DayOfWeek.MONDAY, original.deliveryDay());
        assertEquals(LocalTime.of(8, 0), original.deliveryTime());
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.TUESDAY, LocalTime.of(8, 30, 45)));
        var reloaded = service.config(users.findById(owner.getId()).orElseThrow());
        assertEquals(DayOfWeek.TUESDAY, reloaded.deliveryDay());
        assertEquals(LocalTime.of(8, 30), reloaded.deliveryTime());
        assertEquals("Europe/Madrid", reloaded.timeZone());
        assertEquals(0, summaries.count());
        verifyNoInteractions(mail);
    }

    @Test
    void concurrentDuePollsAndReloadedPollSendOneImmutableSummary() throws Exception {
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.TUESDAY, LocalTime.of(8, 30)));
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); service.sendScheduled(owner, due); return null; });
            var second = executor.submit(() -> { start.await(); service.sendScheduled(owner, due); return null; });
            first.get(60, TimeUnit.SECONDS);
            second.get(60, TimeUnit.SECONDS);
        }
        var friday = service.latestCompletedWeekEnd(due.toLocalDate());
        var saved = summaries.findByUserAndFridayDate(owner, friday).orElseThrow();
        String json = saved.getSnapshotJson();
        assertEquals(due.toInstant(), saved.getScheduledEmailedAt());
        assertEquals(1, summaries.count());
        // A fresh repository read models the persisted state used after a scheduler restart.
        service.sendScheduled(users.findById(owner.getId()).orElseThrow(), due.plusSeconds(20));
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.WEDNESDAY, LocalTime.of(8, 30)));
        service.sendScheduled(users.findById(owner.getId()).orElseThrow(), due.plusDays(1));
        verify(mail, times(1)).send(any(User.class), any(WeeklySummarySnapshot.class), any());
        assertEquals(json, summaries.findByUserAndFridayDate(owner, friday).orElseThrow().getSnapshotJson());
    }

    @Test
    void failedSenderLeavesMarkerUnsetAndRetryReusesSavedEvidence() {
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.TUESDAY, LocalTime.of(8, 30)));
        var friday = service.latestCompletedWeekEnd(due.toLocalDate());
        var saved = service.createForFriday(owner, friday, due.toLocalDate());
        String json = saved.getSnapshotJson();
        doThrow(new IllegalStateException("Simulated SMTP failure")).when(mail).send(any(User.class), any(WeeklySummarySnapshot.class), any());
        assertThrows(IllegalStateException.class, () -> service.sendScheduled(owner, due));
        assertNull(summaries.findByUserAndFridayDate(owner, friday).orElseThrow().getScheduledEmailedAt());
        assertEquals(json, summaries.findByUserAndFridayDate(owner, friday).orElseThrow().getSnapshotJson());
        doNothing().when(mail).send(any(User.class), any(WeeklySummarySnapshot.class), any());
        service.sendScheduled(owner, due.plusSeconds(20));
        assertEquals(due.plusSeconds(20).toInstant(), summaries.findByUserAndFridayDate(owner, friday).orElseThrow().getScheduledEmailedAt());
        assertEquals(json, summaries.findByUserAndFridayDate(owner, friday).orElseThrow().getSnapshotJson());
        verify(mail, times(2)).send(any(User.class), any(WeeklySummarySnapshot.class), any());
    }
}
