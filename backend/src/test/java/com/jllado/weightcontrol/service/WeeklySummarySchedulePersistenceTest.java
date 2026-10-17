package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryScheduleRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.InAppNotificationType;
import com.jllado.weightcontrol.repository.InAppNotificationRepository;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.LocalDate;
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
    @Autowired private InAppNotificationRepository notifications;
    @Autowired private InAppNotificationService notificationService;
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
        notifications.deleteAll();
        users.deleteAll();
    }

    @Test
    void defaultsAndChangedSchedulePersistWithoutSending() {
        var original = service.config(users.findById(owner.getId()).orElseThrow());
        assertEquals(DayOfWeek.MONDAY, original.deliveryDay());
        assertEquals(LocalTime.of(8, 0), original.deliveryTime());
        assertTrue(original.reminderEnabled());
        assertEquals(DayOfWeek.MONDAY, original.reminderDay());
        assertEquals(LocalTime.of(8, 0), original.reminderTime());
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.TUESDAY, LocalTime.of(8, 30, 45), false, DayOfWeek.THURSDAY, LocalTime.of(9, 15, 42)));
        var reloaded = service.config(users.findById(owner.getId()).orElseThrow());
        assertEquals(DayOfWeek.TUESDAY, reloaded.deliveryDay());
        assertEquals(LocalTime.of(8, 30), reloaded.deliveryTime());
        assertFalse(reloaded.reminderEnabled());
        assertEquals(DayOfWeek.THURSDAY, reloaded.reminderDay());
        assertEquals(LocalTime.of(9, 15), reloaded.reminderTime());
        assertEquals("Europe/Madrid", reloaded.timeZone());
        assertEquals(0, summaries.count());
        verifyNoInteractions(mail);
    }

    @Test
    void concurrentDuePollsAndReloadedPollSendOneImmutableSummary() throws Exception {
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.TUESDAY, LocalTime.of(8, 30), true, DayOfWeek.MONDAY, LocalTime.of(8, 0)));
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
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.WEDNESDAY, LocalTime.of(8, 30), true, DayOfWeek.MONDAY, LocalTime.of(8, 0)));
        service.sendScheduled(users.findById(owner.getId()).orElseThrow(), due.plusDays(1));
        verify(mail, times(1)).send(any(User.class), any(WeeklySummarySnapshot.class), any());
        assertEquals(json, summaries.findByUserAndFridayDate(owner, friday).orElseThrow().getSnapshotJson());
    }

    @Test
    void reminderPersistsAcrossFridayAndManualSaveDismissesIt() {
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(8, 0), true, DayOfWeek.TUESDAY, LocalTime.of(8, 30)));

        var reminder = service.remindScheduled(owner, due).orElseThrow();
        assertEquals("WEEKLY_SUMMARY:2026-08-14", reminder.getDeduplicationKey());
        assertEquals("/weekly-summaries?selectedFridayDate=2026-08-14&notificationId=" + reminder.getId(),
            com.jllado.weightcontrol.api.dto.InAppNotificationDtos.PendingNotificationResponse.from(reminder).actionUrl());
        assertEquals(1, notificationService.findPending(owner, due).size());
        assertTrue(service.remindScheduled(owner, due.plusMinutes(1)).isEmpty());
        assertEquals(1, notifications.count());

        service.createForFriday(owner, LocalDate.of(2026, 8, 14), due.toLocalDate());

        assertTrue(notificationService.findPending(owner, due.plusMinutes(2)).isEmpty());
        assertNotNull(notifications.findById(reminder.getId()).orElseThrow().getDismissedAt());
    }

    @Test
    void advancingToANewerEligibleFridayReplacesAnOlderPendingReminder() {
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(8, 0), true, DayOfWeek.TUESDAY, LocalTime.of(8, 30)));
        var older = notificationService.recordWeeklySummaryReminder(owner, LocalDate.of(2026, 8, 14), due.toOffsetDateTime()).orElseThrow();

        var current = service.remindScheduled(owner, due.plusDays(7)).orElseThrow();

        assertEquals(LocalDate.of(2026, 8, 21), current.getReminderDate());
        assertNotNull(notifications.findById(older.getId()).orElseThrow().getDismissedAt());
        assertEquals(1, notificationService.findPending(owner, due.plusDays(7)).size());
    }

    @Test
    void disablingReminderClearsOnlyWeeklySummaryNotifications() {
        var weekly = notificationService.recordWeeklySummaryReminder(owner, LocalDate.of(2026, 8, 14), due.toOffsetDateTime()).orElseThrow();
        var olderWeekly = notificationService.recordWeeklySummaryReminder(owner, LocalDate.of(2026, 8, 7), due.toOffsetDateTime()).orElseThrow();
        var appUpdate = notificationService.recordAppUpdate(owner, "abc123", "A release", due.toOffsetDateTime());

        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.MONDAY, LocalTime.of(8, 0), false, DayOfWeek.TUESDAY, LocalTime.of(8, 30)));

        assertNotNull(notifications.findById(weekly.getId()).orElseThrow().getDismissedAt());
        assertNotNull(notifications.findById(olderWeekly.getId()).orElseThrow().getDismissedAt());
        assertNull(notifications.findById(appUpdate.getId()).orElseThrow().getDismissedAt());
        assertEquals(InAppNotificationType.WEEKLY_SUMMARY, weekly.getType());
    }

    @Test
    void failedSenderLeavesMarkerUnsetAndRetryReusesSavedEvidence() {
        service.updateDeliverySchedule(owner, new WeeklySummaryScheduleRequest(DayOfWeek.TUESDAY, LocalTime.of(8, 30), true, DayOfWeek.MONDAY, LocalTime.of(8, 0)));
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
