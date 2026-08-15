package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.PushDtos.PushKeysRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.PushSubscriptionRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.ReminderSettingsRequest;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.PushSubscription;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.PushSubscriptionRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;
    @Mock
    private RoutineRepository routineRepository;
    @Mock
    private RoutineCheckinRepository checkinRepository;
    @Mock
    private MoodRepository moodRepository;
    @Mock
    private BackPainEpisodeRepository backPainEpisodeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PushGateway gateway;
    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("", "test@example.com"),
            new AppProperties.Push(true, "public", "private", "mailto:test@example.com", "release-token"),
            new AppProperties.WeeklySummary(false, "", "", "", "")
        );
        service = new PushNotificationService(
            subscriptionRepository,
            routineRepository,
            checkinRepository,
            moodRepository,
            backPainEpisodeRepository,
            userRepository,
            gateway,
            new ObjectMapper(),
            properties
        );
    }

    @Test
    void reminderSettingsAreStoredAtMinutePrecision() {
        User user = user(1L);
        when(userRepository.save(user)).thenReturn(user);

        var response = service.updateReminderSettings(user, new ReminderSettingsRequest(
            LocalTime.of(7, 31, 45),
            LocalTime.of(13, 32, 30),
            LocalTime.of(20, 33, 15)
        ));

        assertEquals(LocalTime.of(7, 31), response.morningTime());
        assertEquals(LocalTime.of(13, 32), response.middayTime());
        assertEquals(LocalTime.of(20, 33), response.eveningTime());
        assertEquals("Europe/Madrid", response.timeZone());
        verify(userRepository).save(user);
    }

    @Test
    void reminderSettingsRejectTimesOutsideChronologicalOrder() {
        User user = user(1L);
        ReminderSettingsRequest request = new ReminderSettingsRequest(
            LocalTime.of(13, 30),
            LocalTime.of(13, 30),
            LocalTime.of(20, 30)
        );

        assertThrows(BadRequestException.class, () -> service.updateReminderSettings(user, request));

        verifyNoInteractions(userRepository);
    }

    @Test
    void dailyCheckInReminderSendsMoodAndBackNotificationsToEveryDevice() {
        LocalDate date = LocalDate.of(2026, 8, 13);
        User user = user(1L);
        PushSubscription phone = subscription(10L, user, "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user, "https://push.example/tablet");
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendDailyCheckInReminders(date, LocalTime.of(7, 30, 45));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(4)).send(any(), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getAllValues().stream().anyMatch(value -> value.contains("\"title\":\"Morning mood reminder\"")
            && value.contains("\"url\":\"/?checkInReminder=mood&checkInPeriod=MORNING&checkInReminderDate=2026-08-13\"")
            && value.contains("\"tag\":\"mood-reminder-MORNING\"")));
        assertTrue(payload.getAllValues().stream().anyMatch(value -> value.contains("\"title\":\"Morning back reminder\"")
            && value.contains("\"url\":\"/?checkInReminder=back&checkInPeriod=MORNING&checkInReminderDate=2026-08-13\"")
            && value.contains("\"tag\":\"back-reminder-MORNING\"")));
    }

    @Test
    void dailyCheckInReminderSkipsCompletedMoodAndStillSendsBack() {
        LocalDate date = LocalDate.of(2026, 8, 13);
        User user = user(1L);
        PushSubscription phone = subscription(10L, user, "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user, "https://push.example/tablet");
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(moodRepository.existsByUserAndMoodDateAndPeriod(user, date, MoodPeriod.MIDDAY)).thenReturn(true);
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendDailyCheckInReminders(date, LocalTime.of(13, 30));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).send(any(), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getAllValues().stream().allMatch(value -> value.contains("\"title\":\"Midday back reminder\"")));
    }

    @Test
    void dailyCheckInReminderSkipsCompletedBackAndStillSendsMood() {
        LocalDate date = LocalDate.of(2026, 8, 13);
        User user = user(1L);
        PushSubscription phone = subscription(10L, user, "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user, "https://push.example/tablet");
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(backPainEpisodeRepository.existsByUserAndEpisodeDateAndPeriod(user, date, MoodPeriod.EVENING)).thenReturn(true);
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendDailyCheckInReminders(date, LocalTime.of(20, 30));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).send(any(), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getAllValues().stream().allMatch(value -> value.contains("\"title\":\"Evening mood reminder\"")));
    }

    @Test
    void dailyCheckInReminderUsesEachUsersSchedule() {
        LocalDate date = LocalDate.of(2026, 8, 13);
        User due = user(1L);
        User later = user(2L);
        later.setMorningCheckInReminderTime(LocalTime.of(8, 0));
        PushSubscription duePhone = subscription(10L, due, "https://push.example/due");
        PushSubscription laterPhone = subscription(11L, later, "https://push.example/later");
        when(subscriptionRepository.findAll()).thenReturn(List.of(duePhone, laterPhone));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendDailyCheckInReminders(date, LocalTime.of(7, 30));

        verify(gateway, times(2)).send(eq(duePhone), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        verify(gateway, never()).send(eq(laterPhone), anyString(), anyInt());
    }

    @Test
    void registerReassignsAnExistingBrowserSubscriptionToTheCurrentUser() {
        User previousUser = user(1L);
        User currentUser = user(2L);
        PushSubscription existing = subscription(10L, previousUser, "https://push.example/subscription");
        PushSubscriptionRequest request = new PushSubscriptionRequest(
            existing.getEndpoint(),
            new PushKeysRequest("new-p256dh", "new-auth")
        );
        when(subscriptionRepository.findByEndpointHash(anyString())).thenReturn(Optional.of(existing));

        service.register(currentUser, request);

        assertEquals(currentUser, existing.getUser());
        assertEquals("new-p256dh", existing.getP256dh());
        assertEquals("new-auth", existing.getAuth());
        verify(subscriptionRepository).save(existing);
    }

    @Test
    void unregisterOnlyRemovesTheCurrentUsersDevice() {
        User currentUser = user(1L);
        PushSubscription owned = subscription(10L, currentUser, "https://push.example/owned");
        when(subscriptionRepository.findByEndpointHash(anyString())).thenReturn(Optional.of(owned));

        service.unregister(currentUser, owned.getEndpoint());

        verify(subscriptionRepository).delete(owned);
    }

    @Test
    void unregisterDoesNotRemoveAnotherUsersDevice() {
        User currentUser = user(1L);
        PushSubscription anotherUsers = subscription(10L, user(2L), "https://push.example/other");
        when(subscriptionRepository.findByEndpointHash(anyString())).thenReturn(Optional.of(anotherUsers));

        service.unregister(currentUser, anotherUsers.getEndpoint());

        verify(subscriptionRepository, never()).delete(any());
    }

    @Test
    void routineReminderSendsOneNotificationPerRoutineToEveryDevice() {
        LocalDate date = LocalDate.of(2026, 8, 6);
        LocalTime time = LocalTime.of(13, 7);
        User user = user(1L);
        Routine meditation = routine(20L, user, "Meditation", date.minusDays(10));
        meditation.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-05T13:30:00+02:00"));
        Routine stretching = routine(21L, user, "Stretching", date);
        PushSubscription phone = subscription(10L, user, "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user, "https://push.example/tablet");
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(routineRepository.findByReminderTime(time)).thenReturn(List.of(meditation, stretching));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendRoutineReminders(date, time);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(4)).send(any(), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getAllValues().stream().anyMatch(value -> value.contains("\"body\":\"Meditation\"") && value.contains("\"url\":\"/?routineReminderId=20&routineReminderDate=2026-08-06\"") && value.contains("\"tag\":\"routine-reminder-20\"") && value.contains("\"snoozeUrl\":\"/api/routines/20/reminder-snooze\"")));
        assertTrue(payload.getAllValues().stream().anyMatch(value -> value.contains("\"body\":\"Stretching\"") && value.contains("\"url\":\"/?routineReminderId=21&routineReminderDate=2026-08-06\"") && value.contains("\"tag\":\"routine-reminder-21\"") && value.contains("\"snoozeUrl\":\"/api/routines/21/reminder-snooze\"")));
        assertNull(meditation.getReminderSnoozedUntil());
        verify(routineRepository).save(meditation);
    }

    @Test
    void snoozedRoutineReminderIsDeliveredOnceWhenDue() {
        LocalDate date = LocalDate.of(2026, 8, 6);
        LocalTime time = LocalTime.of(13, 8);
        User user = user(1L);
        Routine routine = routine(20L, user, "Meditation", date.minusDays(10));
        routine.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-06T13:07:30+02:00"));
        PushSubscription phone = subscription(10L, user, "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user, "https://push.example/tablet");
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(routineRepository.findByReminderSnoozedUntilBetween(DateTimes.startOfDay(date), OffsetDateTime.parse("2026-08-06T13:08:00+02:00")))
            .thenReturn(List.of(routine));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendRoutineReminders(date, time);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).send(any(), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getAllValues().stream().allMatch(value -> value.contains("\"body\":\"Meditation\"") && value.contains("\"url\":\"/?routineReminderId=20&routineReminderDate=2026-08-06\"")));
        assertNull(routine.getReminderSnoozedUntil());
        verify(routineRepository).save(routine);
    }

    @Test
    void snoozedRoutineReminderIsConsumedWithoutDeliveryWhenCompleted() {
        LocalDate date = LocalDate.of(2026, 8, 6);
        LocalTime time = LocalTime.of(13, 8);
        User user = user(1L);
        Routine routine = routine(20L, user, "Meditation", date.minusDays(10));
        routine.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-06T13:07:30+02:00"));
        PushSubscription subscription = subscription(10L, user, "https://push.example/phone");
        when(subscriptionRepository.findAll()).thenReturn(List.of(subscription));
        when(routineRepository.findByReminderSnoozedUntilBetween(DateTimes.startOfDay(date), OffsetDateTime.parse("2026-08-06T13:08:00+02:00")))
            .thenReturn(List.of(routine));
        when(checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(routine, DateTimes.startOfDay(date), DateTimes.startOfDay(date.plusDays(1))))
            .thenReturn(true);

        service.sendRoutineReminders(date, time);

        verifyNoInteractions(gateway);
        assertNull(routine.getReminderSnoozedUntil());
        verify(routineRepository).save(routine);
    }

    @Test
    void routineReminderSkipsCompletedAndFutureRoutines() {
        LocalDate date = LocalDate.of(2026, 8, 6);
        LocalTime time = LocalTime.of(18, 30);
        User user = user(1L);
        Routine unfinished = routine(20L, user, "Meditation", date.minusDays(10));
        Routine completed = routine(21L, user, "Stretching", date.minusDays(10));
        Routine future = routine(22L, user, "Walking", date.plusDays(1));
        PushSubscription subscription = subscription(10L, user, "https://push.example/phone");
        when(subscriptionRepository.findAll()).thenReturn(List.of(subscription));
        when(routineRepository.findByReminderTime(time)).thenReturn(List.of(unfinished, completed, future));
        when(checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(any(), any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(0) == completed);
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendRoutineReminders(date, time);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(eq(subscription), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getValue().contains("\"body\":\"Meditation\""));
    }

    @Test
    void routineReminderContinuesAfterFailureAndRemovesExpiredSubscriptions() {
        LocalDate date = LocalDate.of(2026, 8, 6);
        LocalTime time = LocalTime.of(13, 0);
        User user = user(1L);
        Routine routine = routine(20L, user, "Meditation", date);
        PushSubscription failing = subscription(10L, user, "https://push.example/failing");
        PushSubscription expired = subscription(11L, user, "https://push.example/expired");
        when(subscriptionRepository.findAll()).thenReturn(List.of(failing, expired));
        when(routineRepository.findByReminderTime(time)).thenReturn(List.of(routine));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS)))
            .thenThrow(new PushDeliveryException("failed"))
            .thenReturn(410);

        service.sendRoutineReminders(date, time);

        verify(gateway, times(2)).send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        verify(subscriptionRepository).delete(expired);
    }

    @Test
    void testNotificationTargetsTheOwnedDevice() {
        User user = user(1L);
        PushSubscription subscription = subscription(10L, user, "https://push.example/test");
        when(subscriptionRepository.findByEndpointHash(anyString())).thenReturn(Optional.of(subscription));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.TEST_TTL_SECONDS))).thenReturn(201);

        service.sendTest(user, subscription.getEndpoint());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(eq(subscription), payload.capture(), eq(PushNotificationService.TEST_TTL_SECONDS));
        assertTrue(payload.getValue().contains("\"title\":\"Notification test\""));
        assertTrue(payload.getValue().contains("\"body\":\"Notifications are working.\""));
        assertTrue(payload.getValue().contains("\"snoozeUrl\":null"));
    }

    @Test
    void testNotificationRejectsAnotherUsersDevice() {
        User currentUser = user(1L);
        PushSubscription subscription = subscription(10L, user(2L), "https://push.example/test");
        when(subscriptionRepository.findByEndpointHash(anyString())).thenReturn(Optional.of(subscription));

        assertThrows(NotFoundException.class, () -> service.sendTest(currentUser, subscription.getEndpoint()));

        verifyNoInteractions(gateway);
    }

    @Test
    void appUpdateNotificationIsSentToEverySubscribedDevice() {
        PushSubscription phone = subscription(10L, user(1L), "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user(1L), "https://push.example/tablet");
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.APP_UPDATE_TTL_SECONDS))).thenReturn(201);

        service.sendAppUpdate();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).send(any(), payload.capture(), eq(PushNotificationService.APP_UPDATE_TTL_SECONDS));
        assertTrue(payload.getAllValues().stream().allMatch(value -> value.contains("\"title\":\"Weight Control update available\"")
            && value.contains("\"body\":\"Open the app to install the latest version.\"")
            && value.contains("\"url\":\"/\"")
            && value.contains("\"tag\":\"weight-control-update\"")
            && value.contains("\"snoozeUrl\":null")));
    }

    @Test
    void appUpdateNotificationContinuesAfterFailureAndRemovesExpiredSubscriptions() {
        PushSubscription failing = subscription(10L, user(1L), "https://push.example/failing");
        PushSubscription expired = subscription(11L, user(1L), "https://push.example/expired");
        PushSubscription active = subscription(12L, user(1L), "https://push.example/active");
        when(subscriptionRepository.findAll()).thenReturn(List.of(failing, expired, active));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.APP_UPDATE_TTL_SECONDS)))
            .thenThrow(new PushDeliveryException("failed"))
            .thenReturn(410)
            .thenReturn(201);

        service.sendAppUpdate();

        verify(gateway, times(3)).send(any(), anyString(), eq(PushNotificationService.APP_UPDATE_TTL_SECONDS));
        verify(subscriptionRepository).delete(expired);
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static Routine routine(Long id, User user, String name, LocalDate startDate) {
        Routine routine = new Routine();
        routine.setId(id);
        routine.setUser(user);
        routine.setName(name);
        routine.setStartDate(DateTimes.startOfDay(startDate));
        return routine;
    }

    private static PushSubscription subscription(Long id, User user, String endpoint) {
        PushSubscription subscription = new PushSubscription();
        subscription.setId(id);
        subscription.setUser(user);
        subscription.setEndpoint(endpoint);
        subscription.setEndpointHash("hash-" + id);
        subscription.setP256dh("p256dh");
        subscription.setAuth("auth");
        return subscription;
    }
}
