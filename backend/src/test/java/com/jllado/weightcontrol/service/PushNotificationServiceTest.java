package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.PushDtos.PushKeysRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.ReleaseNotificationRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.PushSubscriptionRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.ReminderSettingsRequest;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.PushSubscription;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineReminder;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.PushSubscriptionRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineReminderRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
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
    private RoutineReminderRepository routineReminderRepository;
    @Mock
    private RoutineCheckinRepository checkinRepository;
    @Mock
    private MoodRepository moodRepository;
    @Mock
    private BackPainEpisodeRepository backPainEpisodeRepository;
    @Mock
    private WeightRepository weightRepository;
    @Mock
    private BloodPressureRepository bloodPressureRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InAppNotificationService inAppNotificationService;
    @Mock
    private PushGateway gateway;
    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PushNotificationService(
            subscriptionRepository,
            routineReminderRepository,
            checkinRepository,
            moodRepository,
            backPainEpisodeRepository,
            weightRepository,
            bloodPressureRepository,
            userRepository,
            inAppNotificationService,
            gateway,
            new ObjectMapper(),
            properties(true)
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
        when(userRepository.findAll()).thenReturn(List.of(user));
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
        when(userRepository.findAll()).thenReturn(List.of(user));
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
        when(userRepository.findAll()).thenReturn(List.of(user));
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
        when(userRepository.findAll()).thenReturn(List.of(due, later));
        when(subscriptionRepository.findAll()).thenReturn(List.of(duePhone, laterPhone));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendDailyCheckInReminders(date, LocalTime.of(7, 30));

        verify(gateway, times(2)).send(eq(duePhone), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        verify(gateway, never()).send(eq(laterPhone), anyString(), anyInt());
    }

    @Test
    void dailyCheckInRemindersCreateInboxEntriesWithoutPushSubscriptions() {
        LocalDate date = LocalDate.of(2026, 8, 13);
        User user = user(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(subscriptionRepository.findAll()).thenReturn(List.of());

        service.sendDailyCheckInReminders(date, LocalTime.of(7, 30));

        OffsetDateTime availableAt = OffsetDateTime.parse("2026-08-13T07:30:00+02:00");
        verify(inAppNotificationService).recordMoodReminder(user, MoodPeriod.MORNING, date, availableAt);
        verify(inAppNotificationService).recordBackReminder(user, MoodPeriod.MORNING, date, availableAt);
        verifyNoInteractions(gateway);
    }

    @Test
    void dailyCheckInRemindersCreateInboxEntriesWhenPushIsDisabled() {
        service = new PushNotificationService(
            subscriptionRepository,
            routineReminderRepository,
            checkinRepository,
            moodRepository,
            backPainEpisodeRepository,
            weightRepository,
            bloodPressureRepository,
            userRepository,
            inAppNotificationService,
            gateway,
            new ObjectMapper(),
            properties(false)
        );
        LocalDate date = LocalDate.of(2026, 8, 13);
        User user = user(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));

        service.sendDailyCheckInReminders(date, LocalTime.of(7, 30));

        OffsetDateTime availableAt = OffsetDateTime.parse("2026-08-13T07:30:00+02:00");
        verify(inAppNotificationService).recordMoodReminder(user, MoodPeriod.MORNING, date, availableAt);
        verify(inAppNotificationService).recordBackReminder(user, MoodPeriod.MORNING, date, availableAt);
        verifyNoInteractions(subscriptionRepository, gateway);
    }

    @Test
    void saturdayWeightReminderCreatesAnInboxEntryAndPushesToEveryDevice() {
        LocalDate date = LocalDate.of(2026, 8, 22);
        User user = user(1L);
        PushSubscription phone = subscription(10L, user, "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user, "https://push.example/tablet");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendWeeklyMeasurementReminders(date, LocalTime.of(5, 0, 45));

        OffsetDateTime availableAt = OffsetDateTime.parse("2026-08-22T05:00:00+02:00");
        verify(inAppNotificationService).recordWeightReminder(user, date, availableAt);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).send(any(), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getAllValues().stream().allMatch(value -> value.contains("\"title\":\"Weight reminder\"")
            && value.contains("\"body\":\"Record your weight.\"")
            && value.contains("\"url\":\"/?measurementReminder=weight&measurementReminderDate=2026-08-22\"")
            && value.contains("\"tag\":\"weight-reminder\"")));
    }

    @Test
    void saturdayBloodPressureReminderCreatesAnInboxEntryAndPushesToEveryDevice() {
        LocalDate date = LocalDate.of(2026, 8, 22);
        User user = user(1L);
        PushSubscription phone = subscription(10L, user, "https://push.example/phone");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendWeeklyMeasurementReminders(date, LocalTime.of(5, 15, 30));

        OffsetDateTime availableAt = OffsetDateTime.parse("2026-08-22T05:15:00+02:00");
        verify(inAppNotificationService).recordBloodPressureReminder(user, date, availableAt);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(eq(phone), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getValue().contains("\"title\":\"Blood pressure reminder\"")
            && payload.getValue().contains("\"body\":\"Record your blood pressure.\"")
            && payload.getValue().contains("\"url\":\"/?measurementReminder=blood-pressure&measurementReminderDate=2026-08-22\"")
            && payload.getValue().contains("\"tag\":\"blood-pressure-reminder\""));
    }

    @Test
    void saturdayMeasurementReminderSkipsAnExistingMeasurement() {
        LocalDate date = LocalDate.of(2026, 8, 22);
        User user = user(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(subscriptionRepository.findAll()).thenReturn(List.of());
        when(weightRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            user,
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date.plusDays(1))
        )).thenReturn(true);

        service.sendWeeklyMeasurementReminders(date, PushNotificationService.WEIGHT_REMINDER_TIME);

        verify(inAppNotificationService, never()).recordWeightReminder(any(), any(), any());
        verifyNoInteractions(gateway);
    }

    @Test
    void weeklyMeasurementRemindersIgnoreOtherDaysAndTimes() {
        service.sendWeeklyMeasurementReminders(LocalDate.of(2026, 8, 23), PushNotificationService.WEIGHT_REMINDER_TIME);
        service.sendWeeklyMeasurementReminders(LocalDate.of(2026, 8, 22), LocalTime.of(5, 14));

        verifyNoInteractions(userRepository, subscriptionRepository, weightRepository, bloodPressureRepository, inAppNotificationService, gateway);
    }

    @Test
    void saturdayMeasurementReminderCreatesAnInboxEntryWhenPushIsDisabled() {
        service = new PushNotificationService(
            subscriptionRepository,
            routineReminderRepository,
            checkinRepository,
            moodRepository,
            backPainEpisodeRepository,
            weightRepository,
            bloodPressureRepository,
            userRepository,
            inAppNotificationService,
            gateway,
            new ObjectMapper(),
            properties(false)
        );
        LocalDate date = LocalDate.of(2026, 8, 22);
        User user = user(1L);
        when(userRepository.findAll()).thenReturn(List.of(user));

        service.sendWeeklyMeasurementReminders(date, PushNotificationService.BLOOD_PRESSURE_REMINDER_TIME);

        verify(inAppNotificationService).recordBloodPressureReminder(
            user,
            date,
            OffsetDateTime.parse("2026-08-22T05:15:00+02:00")
        );
        verifyNoInteractions(subscriptionRepository, gateway);
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
    void routineReminderSendsOneNotificationPerDueScheduleToEveryDevice() {
        LocalDate date = LocalDate.of(2026, 8, 6);
        LocalTime time = LocalTime.of(13, 7);
        User user = user(1L);
        Routine meditation = routine(20L, user, "Meditation", date.minusDays(10));
        RoutineReminder meditationReminder = reminder(30L, meditation, time);
        meditationReminder.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-05T13:30:00+02:00"));
        Routine stretching = routine(21L, user, "Stretching", date);
        RoutineReminder stretchingReminder = reminder(31L, stretching, time);
        PushSubscription phone = subscription(10L, user, "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user, "https://push.example/tablet");
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(routineReminderRepository.findByReminderTime(time)).thenReturn(List.of(meditationReminder, stretchingReminder));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendRoutineReminders(date, time);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(4)).send(any(), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        verify(inAppNotificationService).recordRoutineReminder(meditationReminder, date, OffsetDateTime.parse("2026-08-06T13:07:00+02:00"));
        verify(inAppNotificationService).recordRoutineReminder(stretchingReminder, date, OffsetDateTime.parse("2026-08-06T13:07:00+02:00"));
        assertTrue(payload.getAllValues().stream().anyMatch(value -> value.contains("\"body\":\"Meditation\"") && value.contains("\"url\":\"/?routineReminderId=20&routineReminderDate=2026-08-06&routineReminderScheduleId=30\"") && value.contains("\"tag\":\"routine-reminder-30\"") && value.contains("\"snoozeUrl\":\"/api/routines/20/reminders/30/snooze\"")));
        assertTrue(payload.getAllValues().stream().anyMatch(value -> value.contains("\"body\":\"Stretching\"") && value.contains("\"url\":\"/?routineReminderId=21&routineReminderDate=2026-08-06&routineReminderScheduleId=31\"") && value.contains("\"tag\":\"routine-reminder-31\"") && value.contains("\"snoozeUrl\":\"/api/routines/21/reminders/31/snooze\"")));
        assertNull(meditationReminder.getReminderSnoozedUntil());
        verify(routineReminderRepository).save(meditationReminder);
    }

    @Test
    void laterRoutineReminderStillSendsWhenTheRoutineWasNotCompleted() {
        LocalDate date = LocalDate.of(2026, 8, 6);
        User user = user(1L);
        Routine routine = routine(20L, user, "Medication", date.minusDays(10));
        RoutineReminder morning = reminder(30L, routine, LocalTime.of(7, 30));
        RoutineReminder evening = reminder(31L, routine, LocalTime.of(18, 0));
        PushSubscription subscription = subscription(10L, user, "https://push.example/phone");
        when(subscriptionRepository.findAll()).thenReturn(List.of(subscription));
        when(routineReminderRepository.findByReminderTime(LocalTime.of(7, 30))).thenReturn(List.of(morning));
        when(routineReminderRepository.findByReminderTime(LocalTime.of(18, 0))).thenReturn(List.of(evening));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendRoutineReminders(date, LocalTime.of(7, 30));
        service.sendRoutineReminders(date, LocalTime.of(18, 0));

        verify(gateway, times(2)).send(eq(subscription), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        verify(inAppNotificationService).recordRoutineReminder(morning, date, OffsetDateTime.parse("2026-08-06T07:30:00+02:00"));
        verify(inAppNotificationService).recordRoutineReminder(evening, date, OffsetDateTime.parse("2026-08-06T18:00:00+02:00"));
    }

    @Test
    void snoozedRoutineReminderIsDeliveredOnceWhenDue() {
        LocalDate date = LocalDate.of(2026, 8, 6);
        LocalTime time = LocalTime.of(13, 8);
        User user = user(1L);
        Routine routine = routine(20L, user, "Meditation", date.minusDays(10));
        RoutineReminder reminder = reminder(30L, routine, LocalTime.of(7, 30));
        reminder.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-06T13:07:30+02:00"));
        PushSubscription phone = subscription(10L, user, "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user, "https://push.example/tablet");
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(routineReminderRepository.findByReminderSnoozedUntilBetween(DateTimes.startOfDay(date), OffsetDateTime.parse("2026-08-06T13:08:00+02:00")))
            .thenReturn(List.of(reminder));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendRoutineReminders(date, time);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).send(any(), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getAllValues().stream().allMatch(value -> value.contains("\"body\":\"Meditation\"") && value.contains("&routineReminderScheduleId=30\"")));
        assertNull(reminder.getReminderSnoozedUntil());
        verify(routineReminderRepository).save(reminder);
    }

    @Test
    void snoozedRoutineReminderIsConsumedWithoutDeliveryWhenCompleted() {
        LocalDate date = LocalDate.of(2026, 8, 6);
        LocalTime time = LocalTime.of(13, 8);
        User user = user(1L);
        Routine routine = routine(20L, user, "Meditation", date.minusDays(10));
        RoutineReminder reminder = reminder(30L, routine, LocalTime.of(7, 30));
        reminder.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-06T13:07:30+02:00"));
        PushSubscription subscription = subscription(10L, user, "https://push.example/phone");
        when(subscriptionRepository.findAll()).thenReturn(List.of(subscription));
        when(routineReminderRepository.findByReminderSnoozedUntilBetween(DateTimes.startOfDay(date), OffsetDateTime.parse("2026-08-06T13:08:00+02:00")))
            .thenReturn(List.of(reminder));
        when(checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(routine, DateTimes.startOfDay(date), DateTimes.startOfDay(date.plusDays(1))))
            .thenReturn(true);

        service.sendRoutineReminders(date, time);

        verifyNoInteractions(gateway);
        assertNull(reminder.getReminderSnoozedUntil());
        verify(routineReminderRepository).save(reminder);
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
        when(routineReminderRepository.findByReminderTime(time)).thenReturn(List.of(
            reminder(30L, unfinished, time),
            reminder(31L, completed, time),
            reminder(32L, future, time)
        ));
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
        RoutineReminder reminder = reminder(30L, routine, time);
        PushSubscription failing = subscription(10L, user, "https://push.example/failing");
        PushSubscription expired = subscription(11L, user, "https://push.example/expired");
        when(subscriptionRepository.findAll()).thenReturn(List.of(failing, expired));
        when(routineReminderRepository.findByReminderTime(time)).thenReturn(List.of(reminder));
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
        User owner = user(1L);
        PushSubscription phone = subscription(10L, user(1L), "https://push.example/phone");
        PushSubscription tablet = subscription(11L, user(1L), "https://push.example/tablet");
        when(userRepository.findAll()).thenReturn(List.of(owner));
        when(subscriptionRepository.findAll()).thenReturn(List.of(phone, tablet));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.APP_UPDATE_TTL_SECONDS))).thenReturn(201);

        service.sendAppUpdate(releaseNotificationRequest());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(inAppNotificationService).recordAppUpdate(
            eq(owner),
            eq("d88c96a4c5ac69e262e6d92fbb42c91e220c74a5"),
            eq("Allow workout exercise reordering"),
            any(OffsetDateTime.class)
        );
        verify(gateway, times(2)).send(any(), payload.capture(), eq(PushNotificationService.APP_UPDATE_TTL_SECONDS));
        assertTrue(payload.getAllValues().stream().allMatch(value -> value.contains("\"title\":\"Weight Control update available\"")
            && value.contains("\"body\":\"Allow workout exercise reordering\"")
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

        service.sendAppUpdate(releaseNotificationRequest());

        verify(gateway, times(3)).send(any(), anyString(), eq(PushNotificationService.APP_UPDATE_TTL_SECONDS));
        verify(subscriptionRepository).delete(expired);
    }

    private static ReleaseNotificationRequest releaseNotificationRequest() {
        return new ReleaseNotificationRequest(
            "d88c96a4c5ac69e262e6d92fbb42c91e220c74a5",
            "Allow workout exercise reordering"
        );
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

    private static RoutineReminder reminder(Long id, Routine routine, LocalTime time) {
        RoutineReminder reminder = new RoutineReminder();
        reminder.setId(id);
        reminder.setRoutine(routine);
        reminder.setReminderTime(time);
        return reminder;
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

    private static AppProperties properties(boolean pushEnabled) {
        return new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("", "test@example.com"),
            new AppProperties.Push(pushEnabled, "public", "private", "mailto:test@example.com", "release-token"),
            new AppProperties.WeeklySummary(false, "", "", "", "")
        );
    }
}
