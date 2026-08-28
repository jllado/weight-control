package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.jllado.weightcontrol.domain.InAppNotification;
import com.jllado.weightcontrol.domain.InAppNotificationType;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineReminder;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.PersonalRecordDirection;
import com.jllado.weightcontrol.domain.PersonalRecordDomain;
import com.jllado.weightcontrol.domain.PersonalRecordEventKind;
import com.jllado.weightcontrol.domain.PersonalRecordMetric;
import com.jllado.weightcontrol.domain.PersonalRecordSourceType;
import com.jllado.weightcontrol.domain.PersonalRecordUnit;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.PersonalRecordSourceResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.PersonalRecordSubjectResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.PersonalRecordQualifierResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordAchievementResponse;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.InAppNotificationRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InAppNotificationServiceTest {

    @Mock
    private InAppNotificationRepository repository;
    @Mock
    private RoutineCheckinRepository routineCheckinRepository;
    @Mock
    private MoodRepository moodRepository;
    @Mock
    private BackPainEpisodeRepository backPainEpisodeRepository;
    @Mock
    private WeightRepository weightRepository;
    @Mock
    private BloodPressureRepository bloodPressureRepository;
    private InAppNotificationService service;

    @BeforeEach
    void setUp() {
        service = new InAppNotificationService(
            repository,
            routineCheckinRepository,
            moodRepository,
            backPainEpisodeRepository,
            weightRepository,
            bloodPressureRepository
        );
    }

    @Test
    void reminderRecordingUsesStableKeysAndUpdatesExistingNotifications() {
        User user = user(1L);
        Routine routine = routine(2L, user);
        RoutineReminder reminder = reminder(3L, routine);
        LocalDate date = LocalDate.of(2026, 8, 20);
        OffsetDateTime firstTime = OffsetDateTime.parse("2026-08-20T07:30:00+02:00");
        OffsetDateTime secondTime = OffsetDateTime.parse("2026-08-20T07:45:00+02:00");
        InAppNotification existing = new InAppNotification();
        when(repository.findByUserAndDeduplicationKey(user, "ROUTINE:3:2026-08-20"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));

        service.recordRoutineReminder(reminder, date, firstTime);
        service.recordRoutineReminder(reminder, date, secondTime);

        assertEquals(InAppNotificationType.ROUTINE, existing.getType());
        assertEquals(reminder, existing.getRoutineReminder());
        assertEquals("Routine reminder", existing.getTitle());
        assertEquals("Meditation", existing.getMessage());
        assertEquals(secondTime, existing.getAvailableAt());
        verify(repository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any(InAppNotification.class));
    }

    @Test
    void laterRoutineReminderDismissesEarlierSchedulesForTheSameRoutineAndDate() {
        User user = user(1L);
        Routine routine = routine(2L, user);
        RoutineReminder morning = reminder(3L, routine);
        RoutineReminder evening = reminder(4L, routine);
        LocalDate date = LocalDate.of(2026, 8, 20);
        OffsetDateTime morningTime = OffsetDateTime.parse("2026-08-20T07:30:00+02:00");
        OffsetDateTime eveningTime = OffsetDateTime.parse("2026-08-20T18:00:00+02:00");
        InAppNotification morningNotification = routineNotification(1L, user, morning, date, morningTime);
        when(repository.findPendingRoutineNotificationsForRoutine(user, InAppNotificationType.ROUTINE, routine, evening, date))
            .thenReturn(List.of(morningNotification));
        when(repository.findByUserAndDeduplicationKey(user, "ROUTINE:4:2026-08-20")).thenReturn(Optional.empty());

        service.recordRoutineReminder(evening, date, eveningTime);

        assertEquals(eveningTime, morningNotification.getDismissedAt());
        verify(repository).saveAll(List.of(morningNotification));
        var notification = org.mockito.ArgumentCaptor.forClass(InAppNotification.class);
        verify(repository).save(notification.capture());
        assertEquals(evening, notification.getValue().getRoutineReminder());
        assertNull(notification.getValue().getDismissedAt());
    }

    @Test
    void personalRecordNotificationPersistsWithAnExactHistoryLink() {
        User user = user(1L);
        RecordAchievementResponse achievement = new RecordAchievementResponse(
            "event-key", PersonalRecordMetric.ROUTINE_BEST_STREAK_MAXIMUM, "Highest routine best streak",
            PersonalRecordDomain.BEHAVIOR, PersonalRecordDirection.MAXIMUM, PersonalRecordEventKind.IMPROVED,
            new BigDecimal("60"), new BigDecimal("21"), PersonalRecordUnit.DAYS, LocalDate.of(2026, 8, 20),
            new PersonalRecordSubjectResponse("ROUTINE", 2L, "Meditation"), null,
            new PersonalRecordSourceResponse(PersonalRecordSourceType.ROUTINE_CHECKIN, 3L, null, null)
        );
        when(repository.findByUserAndDeduplicationKey(user, "PERSONAL_RECORD:event-key")).thenReturn(Optional.empty());

        service.recordPersonalRecords(user, List.of(achievement));

        var notification = org.mockito.ArgumentCaptor.forClass(InAppNotification.class);
        verify(repository).save(notification.capture());
        assertEquals(InAppNotificationType.PERSONAL_RECORD, notification.getValue().getType());
        assertEquals("Highest routine best streak — Meditation: 60 days", notification.getValue().getMessage());
        assertEquals("/records?tab=history&eventKey=event-key", notification.getValue().getActionUrl());
    }

    @Test
    void personalRecordNotificationIncludesTheLoadQualifier() {
        User user = user(1L);
        RecordAchievementResponse achievement = new RecordAchievementResponse(
            "event-key", PersonalRecordMetric.WORKOUT_REPETITIONS, "Most repetitions",
            PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordEventKind.IMPROVED,
            new BigDecimal("20"), new BigDecimal("18"), PersonalRecordUnit.REPETITIONS, LocalDate.of(2026, 8, 20),
            new PersonalRecordSubjectResponse("EXERCISE", 2L, "Hip thrust"), new PersonalRecordQualifierResponse(new BigDecimal("10"), "10 kg"),
            new PersonalRecordSourceResponse(PersonalRecordSourceType.WORKOUT, 3L, 1, 1)
        );
        when(repository.findByUserAndDeduplicationKey(user, "PERSONAL_RECORD:event-key")).thenReturn(Optional.empty());

        service.recordPersonalRecords(user, List.of(achievement));

        var notification = org.mockito.ArgumentCaptor.forClass(InAppNotification.class);
        verify(repository).save(notification.capture());
        assertEquals("Most repetitions — Hip thrust at 10 kg: 20 repetitions", notification.getValue().getMessage());
    }

    @Test
    void pendingReturnsOnlyIncompleteNotificationsForToday() {
        User user = user(1L);
        LocalDate date = LocalDate.of(2026, 8, 20);
        ZonedDateTime now = ZonedDateTime.parse("2026-08-20T13:45:00+02:00[Europe/Madrid]");
        InAppNotification routine = routineNotification(1L, user, reminder(3L, routine(2L, user)), date, now.minusHours(6).toOffsetDateTime());
        InAppNotification mood = checkInNotification(2L, user, InAppNotificationType.MOOD, MoodPeriod.MIDDAY, date, now.minusMinutes(15).toOffsetDateTime());
        InAppNotification back = checkInNotification(3L, user, InAppNotificationType.BACK, MoodPeriod.MIDDAY, date, now.minusMinutes(15).toOffsetDateTime());
        InAppNotification weight = notification(4L, user, InAppNotificationType.WEIGHT, date, now.minusMinutes(10).toOffsetDateTime());
        InAppNotification bloodPressure = notification(5L, user, InAppNotificationType.BLOOD_PRESSURE, date, now.minusMinutes(5).toOffsetDateTime());
        InAppNotification appUpdate = notification(6L, user, InAppNotificationType.APP_UPDATE, date.minusDays(2), now.minusDays(2).toOffsetDateTime());
        when(repository.findPending(
            user,
            date,
            now.toOffsetDateTime(),
            Set.of(InAppNotificationType.APP_UPDATE, InAppNotificationType.PERSONAL_RECORD)
        )).thenReturn(List.of(appUpdate, routine, mood, back, weight, bloodPressure));
        when(moodRepository.existsByUserAndMoodDateAndPeriod(user, date, MoodPeriod.MIDDAY)).thenReturn(true);
        when(weightRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            user,
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date.plusDays(1))
        )).thenReturn(true);

        List<InAppNotification> pending = service.findPending(user, now);

        assertEquals(List.of(appUpdate, routine, back, bloodPressure), pending);
        verify(routineCheckinRepository).existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine.getRoutineReminder().getRoutine(),
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date.plusDays(1))
        );
        verify(backPainEpisodeRepository).existsByUserAndEpisodeDateAndPeriod(user, date, MoodPeriod.MIDDAY);
        verify(bloodPressureRepository).existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            user,
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date.plusDays(1))
        );
    }

    @Test
    void measurementRemindersUseStableKeysAndContent() {
        User user = user(1L);
        LocalDate date = LocalDate.of(2026, 8, 22);
        OffsetDateTime weightTime = OffsetDateTime.parse("2026-08-22T05:00:00+02:00");
        OffsetDateTime bloodPressureTime = OffsetDateTime.parse("2026-08-22T05:15:00+02:00");
        when(repository.findByUserAndDeduplicationKey(user, "WEIGHT:2026-08-22")).thenReturn(Optional.empty());
        when(repository.findByUserAndDeduplicationKey(user, "BLOOD_PRESSURE:2026-08-22")).thenReturn(Optional.empty());

        service.recordWeightReminder(user, date, weightTime);
        service.recordBloodPressureReminder(user, date, bloodPressureTime);

        var notifications = org.mockito.ArgumentCaptor.forClass(InAppNotification.class);
        verify(repository, org.mockito.Mockito.times(2)).save(notifications.capture());
        InAppNotification weight = notifications.getAllValues().get(0);
        assertEquals(InAppNotificationType.WEIGHT, weight.getType());
        assertEquals("Weight reminder", weight.getTitle());
        assertEquals("Record your weight.", weight.getMessage());
        assertEquals(weightTime, weight.getAvailableAt());
        assertEquals("WEIGHT:2026-08-22", weight.getDeduplicationKey());
        InAppNotification bloodPressure = notifications.getAllValues().get(1);
        assertEquals(InAppNotificationType.BLOOD_PRESSURE, bloodPressure.getType());
        assertEquals("Blood pressure reminder", bloodPressure.getTitle());
        assertEquals("Record your blood pressure.", bloodPressure.getMessage());
        assertEquals(bloodPressureTime, bloodPressure.getAvailableAt());
        assertEquals("BLOOD_PRESSURE:2026-08-22", bloodPressure.getDeduplicationKey());
    }

    @Test
    void appUpdateUsesTheCommitForDeduplicationAndRemainsDismissedOnRetry() {
        User user = user(1L);
        String commitSha = "d88c96a4c5ac69e262e6d92fbb42c91e220c74a5";
        OffsetDateTime availableAt = OffsetDateTime.parse("2026-08-20T21:45:00+02:00");
        InAppNotification existing = new InAppNotification();
        existing.setDismissedAt(OffsetDateTime.parse("2026-08-20T22:00:00+02:00"));
        when(repository.findByUserAndDeduplicationKey(user, "APP_UPDATE:" + commitSha))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));

        service.recordAppUpdate(user, commitSha, "Allow workout exercise reordering", availableAt);
        service.recordAppUpdate(user, commitSha, "Allow workout exercise reordering", availableAt.plusMinutes(15));

        var notifications = org.mockito.ArgumentCaptor.forClass(InAppNotification.class);
        verify(repository).save(notifications.capture());
        InAppNotification notification = notifications.getValue();
        assertEquals(InAppNotificationType.APP_UPDATE, notification.getType());
        assertEquals(LocalDate.of(2026, 8, 20), notification.getReminderDate());
        assertEquals("Weight Control update available", notification.getTitle());
        assertEquals("Allow workout exercise reordering", notification.getMessage());
        assertEquals(availableAt, notification.getAvailableAt());
        assertEquals("APP_UPDATE:" + commitSha, notification.getDeduplicationKey());
        assertEquals(OffsetDateTime.parse("2026-08-20T22:00:00+02:00"), existing.getDismissedAt());
    }

    @Test
    void snoozeHidesTheRoutineUntilItsNextAvailabilityOrMidnight() {
        User user = user(1L);
        Routine routine = routine(2L, user);
        RoutineReminder reminder = reminder(3L, routine);
        LocalDate date = LocalDate.of(2026, 8, 20);
        InAppNotification notification = routineNotification(
            1L,
            user,
            reminder,
            date,
            OffsetDateTime.parse("2026-08-20T07:30:00+02:00")
        );
        when(repository.findByUserAndDeduplicationKey(user, "ROUTINE:3:2026-08-20")).thenReturn(Optional.of(notification));

        OffsetDateTime nextReminderAt = OffsetDateTime.parse("2026-08-20T08:00:00+02:00");
        service.snoozeRoutineReminder(reminder, date, nextReminderAt);
        assertEquals(nextReminderAt, notification.getAvailableAt());

        service.snoozeRoutineReminder(reminder, date, null);
        assertEquals(DateTimes.startOfDay(date.plusDays(1)), notification.getAvailableAt());
    }

    @Test
    void dismissRequiresAnOwnedNotification() {
        User user = user(1L);
        when(repository.findByIdAndUser(10L, user)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.dismiss(user, 10L));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dismissAllMarksCurrentPendingNotifications() {
        User user = user(1L);
        InAppNotification notification = notification(
            10L,
            user,
            InAppNotificationType.APP_UPDATE,
            LocalDate.of(2026, 8, 22),
            OffsetDateTime.parse("2026-08-22T05:00:00+02:00")
        );
        when(repository.findPending(eq(user), any(LocalDate.class), any(OffsetDateTime.class), eq(Set.of(InAppNotificationType.APP_UPDATE, InAppNotificationType.PERSONAL_RECORD))))
            .thenReturn(List.of(notification));

        service.dismissAll(user);

        verify(repository).saveAll(List.of(notification));
        org.junit.jupiter.api.Assertions.assertNotNull(notification.getDismissedAt());
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static Routine routine(Long id, User user) {
        Routine routine = new Routine();
        routine.setId(id);
        routine.setUser(user);
        routine.setName("Meditation");
        return routine;
    }

    private static InAppNotification routineNotification(
        Long id,
        User user,
        RoutineReminder reminder,
        LocalDate date,
        OffsetDateTime availableAt
    ) {
        InAppNotification notification = notification(id, user, InAppNotificationType.ROUTINE, date, availableAt);
        notification.setRoutineReminder(reminder);
        return notification;
    }

    private static RoutineReminder reminder(Long id, Routine routine) {
        RoutineReminder reminder = new RoutineReminder();
        reminder.setId(id);
        reminder.setRoutine(routine);
        return reminder;
    }

    private static InAppNotification checkInNotification(
        Long id,
        User user,
        InAppNotificationType type,
        MoodPeriod period,
        LocalDate date,
        OffsetDateTime availableAt
    ) {
        InAppNotification notification = notification(id, user, type, date, availableAt);
        notification.setPeriod(period);
        return notification;
    }

    private static InAppNotification notification(
        Long id,
        User user,
        InAppNotificationType type,
        LocalDate date,
        OffsetDateTime availableAt
    ) {
        InAppNotification notification = new InAppNotification();
        notification.setId(id);
        notification.setUser(user);
        notification.setType(type);
        notification.setReminderDate(date);
        notification.setAvailableAt(availableAt);
        return notification;
    }
}
