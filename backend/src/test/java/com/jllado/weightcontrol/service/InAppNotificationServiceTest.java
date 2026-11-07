package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @Mock private com.jllado.weightcontrol.repository.RoutineRepository routineRepository;
    private InAppNotificationService service;

    @BeforeEach
    void setUp() {
        service = new InAppNotificationService(
            repository,
            routineCheckinRepository,
            moodRepository,
            backPainEpisodeRepository,
            weightRepository,
            bloodPressureRepository,
            routineRepository
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
    void reschedulesMeasurementOccurrenceBeforeItsNextWeeklyOccurrence() {
        User user = user(1L);
        user.setWeightReminderTime(java.time.LocalTime.of(6, 0));
        LocalDate originalDate = LocalDate.now(DateTimes.USER_ZONE);
        user.setWeightReminderDay(originalDate.getDayOfWeek());
        InAppNotification notification = new InAppNotification();
        notification.setId(50L);
        notification.setUser(user);
        notification.setType(InAppNotificationType.WEIGHT);
        notification.setReminderDate(originalDate);
        notification.setAvailableAt(originalDate.atTime(6, 0).atZone(DateTimes.USER_ZONE).toOffsetDateTime());
        notification.setDeduplicationKey("WEIGHT:" + originalDate);
        when(repository.findByIdAndUser(50L, user)).thenReturn(Optional.of(notification));
        canonicalMetric(user, com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT, originalDate.getDayOfWeek(), java.time.LocalTime.of(6, 0));
        LocalDate movedDate = originalDate.plusDays(1);

        service.reschedule(user, 50L, movedDate, java.time.LocalTime.of(8, 0));

        assertEquals(movedDate, notification.getReminderDate());
        assertEquals(movedDate.atTime(8, 0).atZone(DateTimes.USER_ZONE).toOffsetDateTime(), notification.getAvailableAt());
        org.junit.jupiter.api.Assertions.assertTrue(notification.isRescheduled());
        assertEquals(java.time.LocalTime.of(6, 0), user.getWeightReminderTime());
        verify(repository).save(notification);
    }

    @Test
    void snoozesCheckInWithinTheReminderDay() {
        User user = user(1L);
        user.setMorningCheckInReminderTime(java.time.LocalTime.of(7, 30));
        LocalDate date = LocalDate.of(2026, 8, 20);
        ZonedDateTime now = ZonedDateTime.parse("2026-08-20T10:00:00+02:00[Europe/Madrid]");
        InAppNotification notification = checkInNotification(56L, user, InAppNotificationType.MOOD, MoodPeriod.MORNING, date, now.minusMinutes(5).toOffsetDateTime());
        notification.setDeduplicationKey("MOOD:MORNING:" + date);
        when(repository.findByIdAndUser(56L, user)).thenReturn(Optional.of(notification));

        OffsetDateTime nextReminderAt = service.snooze(user, 56L, 30, now);

        assertEquals(now.plusMinutes(30).toOffsetDateTime(), nextReminderAt);
        assertEquals(date, notification.getReminderDate());
        assertEquals(nextReminderAt, notification.getAvailableAt());
        org.junit.jupiter.api.Assertions.assertTrue(notification.isRescheduled());
        org.junit.jupiter.api.Assertions.assertFalse(notification.isRescheduleDelivered());
        verify(repository).save(notification);
    }

    @Test
    void checkInSnoozeCrossingMadridMidnightExpiresThatOccurrence() {
        User user = user(1L);
        LocalDate date = LocalDate.of(2026, 8, 20);
        ZonedDateTime now = ZonedDateTime.parse("2026-08-20T23:50:00+02:00[Europe/Madrid]");
        InAppNotification notification = checkInNotification(57L, user, InAppNotificationType.BACK, MoodPeriod.EVENING, date, now.minusMinutes(5).toOffsetDateTime());
        when(repository.findByIdAndUser(57L, user)).thenReturn(Optional.of(notification));

        OffsetDateTime nextReminderAt = service.snooze(user, 57L, 15, now);

        assertNull(nextReminderAt);
        assertEquals(DateTimes.startOfDay(date.plusDays(1)), notification.getAvailableAt());
        org.junit.jupiter.api.Assertions.assertFalse(notification.isRescheduled());
        org.junit.jupiter.api.Assertions.assertTrue(notification.isRescheduleDelivered());
        verify(repository).save(notification);
    }

    @Test
    void measurementSnoozeMayMoveToTomorrowBeforeTheNextConfiguredOccurrence() {
        User user = user(1L);
        user.setWeightReminderTime(java.time.LocalTime.of(6, 0));
        LocalDate date = LocalDate.of(2026, 8, 20);
        user.setWeightReminderDay(date.getDayOfWeek());
        ZonedDateTime now = ZonedDateTime.parse("2026-08-20T23:50:00+02:00[Europe/Madrid]");
        InAppNotification notification = notification(58L, user, InAppNotificationType.WEIGHT, date, now.minusMinutes(5).toOffsetDateTime());
        notification.setDeduplicationKey("WEIGHT:" + date);
        when(repository.findByIdAndUser(58L, user)).thenReturn(Optional.of(notification));

        canonicalMetric(user, com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT, date.getDayOfWeek(), java.time.LocalTime.of(6, 0));
        OffsetDateTime nextReminderAt = service.snooze(user, 58L, 30, now);

        assertEquals(now.plusMinutes(30).toOffsetDateTime(), nextReminderAt);
        assertEquals(date.plusDays(1), notification.getReminderDate());
        org.junit.jupiter.api.Assertions.assertTrue(notification.isRescheduled());
        org.junit.jupiter.api.Assertions.assertFalse(notification.isRescheduleDelivered());
        verify(repository).save(notification);
    }

    @Test
    void measurementSnoozeCannotReachTheNextConfiguredOccurrence() {
        User user = user(1L);
        user.setWeightReminderTime(java.time.LocalTime.of(6, 0));
        LocalDate date = LocalDate.of(2026, 8, 20);
        user.setWeightReminderDay(date.getDayOfWeek());
        ZonedDateTime now = ZonedDateTime.parse("2026-08-20T05:50:00+02:00[Europe/Madrid]");
        InAppNotification notification = notification(59L, user, InAppNotificationType.WEIGHT, date.minusDays(1), now.minusMinutes(20).toOffsetDateTime());
        notification.setDeduplicationKey("WEIGHT:" + date.minusDays(1));
        notification.setRescheduled(true);
        when(repository.findByIdAndUser(59L, user)).thenReturn(Optional.of(notification));

        canonicalMetric(user, com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT, date.getDayOfWeek(), java.time.LocalTime.of(6, 0));
        assertThrows(BadRequestException.class, () -> service.snooze(user, 59L, 15, now));
        verify(repository, never()).save(notification);
    }

    @Test
    void snoozeRejectsUnsupportedCompletedUnownedAndUnlistedReminders() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        ZonedDateTime now = ZonedDateTime.now(DateTimes.USER_ZONE).withSecond(0).withNano(0);
        InAppNotification appUpdate = notification(60L, user, InAppNotificationType.APP_UPDATE, date, now.minusMinutes(5).toOffsetDateTime());
        InAppNotification completedWeight = notification(61L, user, InAppNotificationType.WEIGHT, date, now.minusMinutes(5).toOffsetDateTime());
        when(repository.findByIdAndUser(60L, user)).thenReturn(Optional.of(appUpdate));
        when(repository.findByIdAndUser(61L, user)).thenReturn(Optional.of(completedWeight));
        when(repository.findByIdAndUser(62L, user)).thenReturn(Optional.empty());
        when(weightRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            user,
            DateTimes.startOfDay(com.jllado.weightcontrol.util.RoutinePeriods.start(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY, date)),
            DateTimes.startOfDay(com.jllado.weightcontrol.util.RoutinePeriods.nextStart(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY, date))
        )).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.snooze(user, 60L, 15, now));
        assertThrows(BadRequestException.class, () -> service.snooze(user, 61L, 15, now));
        assertThrows(NotFoundException.class, () -> service.snooze(user, 62L, 15, now));
        assertThrows(BadRequestException.class, () -> service.snooze(user, 61L, 10, now));
        verify(repository, never()).save(any(InAppNotification.class));
    }

    @Test
    void rejectsRescheduleAtTheNextMeasurementOccurrence() {
        User user = user(1L);
        user.setWeightReminderTime(java.time.LocalTime.of(6, 0));
        LocalDate originalDate = LocalDate.now(DateTimes.USER_ZONE);
        user.setWeightReminderDay(originalDate.getDayOfWeek());
        InAppNotification notification = new InAppNotification();
        notification.setId(51L);
        notification.setUser(user);
        notification.setType(InAppNotificationType.WEIGHT);
        notification.setReminderDate(originalDate);
        notification.setAvailableAt(originalDate.atTime(6, 0).atZone(DateTimes.USER_ZONE).toOffsetDateTime());
        notification.setDeduplicationKey("WEIGHT:" + originalDate);
        when(repository.findByIdAndUser(51L, user)).thenReturn(Optional.of(notification));

        canonicalMetric(user, com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT, originalDate.getDayOfWeek(), java.time.LocalTime.of(6, 0));
        assertThrows(BadRequestException.class, () -> service.reschedule(user, 51L, originalDate.plusDays(7), java.time.LocalTime.of(6, 0)));
        verify(repository, never()).save(notification);
    }

    @Test
    void recurringDayChangeBoundsBothMeasurementsFromTheirOriginalOccurrence() {
        LocalDate originalDate = LocalDate.now(DateTimes.USER_ZONE);
        for (InAppNotificationType type : List.of(InAppNotificationType.WEIGHT, InAppNotificationType.BLOOD_PRESSURE)) {
            User user = user(1L);
            InAppNotification notification = notificationForRescheduling(55L, user, originalDate);
            notification.setType(type);
            notification.setDeduplicationKey(type + ":" + originalDate);
            when(repository.findByIdAndUser(55L, user)).thenReturn(Optional.of(notification));
            LocalDate limit = originalDate.plusDays(type == InAppNotificationType.WEIGHT ? 3 : 4);
            java.time.LocalTime limitTime = type == InAppNotificationType.WEIGHT ? java.time.LocalTime.of(8, 15) : java.time.LocalTime.of(9, 30);
            canonicalMetric(user, type == InAppNotificationType.WEIGHT ? com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT
                : com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.BLOOD_PRESSURE_MEASUREMENT, limit.getDayOfWeek(), limitTime);

            service.reschedule(user, 55L, originalDate.plusDays(1), java.time.LocalTime.NOON);
            service.reschedule(user, 55L, limit, limitTime.minusMinutes(1));
            assertThrows(BadRequestException.class, () -> service.reschedule(user, 55L, limit, limitTime));
            assertEquals(type + ":" + originalDate, notification.getDeduplicationKey());
            assertEquals(limit.atTime(limitTime.minusMinutes(1)).atZone(DateTimes.USER_ZONE).toOffsetDateTime(), notification.getAvailableAt());
        }
    }

    @Test
    void legacyMeasurementUsesTheEarliestCanonicalPairIncludingLaterOnTheSameDay() {
        User owner = user(1L); LocalDate today = LocalDate.now(DateTimes.USER_ZONE);
        LocalDate day = today.plusDays(2);
        Routine assigned = canonicalMetric(owner, com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT,
            day.getDayOfWeek(), java.time.LocalTime.of(9, 0));
        // Automation pause does not disable the still-configured reminder pairs.
        assigned.setAutomaticCompletionEnabled(false);
        var later = reminder(9L, assigned); later.setDay(day.plusDays(1).getDayOfWeek()); later.setReminderTime(java.time.LocalTime.of(7, 0));
        assigned.getReminders().add(later);
        var notification = notificationForRescheduling(56L, owner, today);
        notification.setAvailableAt(day.atTime(8, 0).atZone(DateTimes.USER_ZONE).toOffsetDateTime());
        notification.setRescheduled(true);
        when(repository.findByIdAndUser(56L, owner)).thenReturn(Optional.of(notification));
        service.reschedule(owner, 56L, day, java.time.LocalTime.of(8, 59));
        assertThrows(BadRequestException.class, () -> service.reschedule(owner, 56L, day, java.time.LocalTime.of(9, 0)));
        assertEquals(java.time.LocalTime.of(9, 0), assigned.getReminders().iterator().next().getReminderTime());
        assertEquals(java.time.LocalTime.of(5, 0), owner.getWeightReminderTime());
    }

    @Test
    void weeklyRoutineRescheduleStopsAtTheEarliestLaterWeekdayPair() {
        User owner = user(1L);
        LocalDate saturday = LocalDate.now(DateTimes.USER_ZONE).with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.SATURDAY));
        Routine assigned = routine(20L, owner); assigned.setCadence(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY);
        assigned.setAutomaticTrigger(com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT);
        var first = reminder(30L, assigned); first.setDay(java.time.DayOfWeek.SATURDAY); first.setReminderTime(java.time.LocalTime.of(5,0));
        var next = reminder(31L, assigned); next.setDay(java.time.DayOfWeek.MONDAY); next.setReminderTime(java.time.LocalTime.of(6,0));
        assigned.getReminders().addAll(List.of(first, next));
        var notification = routineNotification(58L, owner, first, saturday, DateTimes.startOfDay(saturday).plusHours(5));
        when(repository.findByIdAndUser(58L, owner)).thenReturn(Optional.of(notification));
        service.reschedule(owner, 58L, saturday.plusDays(1), java.time.LocalTime.NOON);
        assertThrows(BadRequestException.class, () -> service.reschedule(owner, 58L, saturday.plusDays(2), java.time.LocalTime.of(6,0)));
        assertEquals(saturday.plusDays(1), notification.getReminderDate());
    }

    @Test
    void weeklyRoutineSnoozeCannotCrossTheNextPairEvenOnTheSameDay() {
        User owner = user(1L); LocalDate monday = LocalDate.of(2026,11,9);
        Routine assigned = routine(20L, owner); assigned.setCadence(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY);
        assigned.setAutomaticTrigger(com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT);
        var saturday = reminder(30L, assigned); saturday.setDay(java.time.DayOfWeek.SATURDAY); saturday.setReminderTime(java.time.LocalTime.of(5,0));
        var firstMonday = reminder(31L, assigned); firstMonday.setDay(java.time.DayOfWeek.MONDAY); firstMonday.setReminderTime(java.time.LocalTime.of(7,0));
        var laterMonday = reminder(32L, assigned); laterMonday.setDay(java.time.DayOfWeek.MONDAY); laterMonday.setReminderTime(java.time.LocalTime.of(8,0));
        assigned.getReminders().addAll(List.of(saturday, firstMonday, laterMonday));
        var notification = routineNotification(59L, owner, saturday, monday.minusDays(2), DateTimes.startOfDay(monday).plusHours(6));
        notification.setRescheduled(true);
        when(repository.findByIdAndUser(59L, owner)).thenReturn(Optional.of(notification));
        ZonedDateTime now = monday.atTime(6,50).atZone(DateTimes.USER_ZONE);
        assertThrows(BadRequestException.class, () -> service.snooze(owner, 59L, 15, now));
        notification.setAvailableAt(DateTimes.startOfDay(monday).plusHours(7));
        assertThrows(BadRequestException.class, () -> service.snooze(owner, 59L, 15, monday.atTime(7,50).atZone(DateTimes.USER_ZONE)));
        verify(repository, never()).save(notification);
    }

    @Test
    void deletedOrUnscheduledMeasurementRoutineHasNoRecurringUpperBound() {
        User owner = user(1L); LocalDate today = LocalDate.now(DateTimes.USER_ZONE);
        var notification = notificationForRescheduling(57L, owner, today);
        when(repository.findByIdAndUser(57L, owner)).thenReturn(Optional.of(notification));
        when(routineRepository.findByUserOrderByStartDateAsc(owner)).thenReturn(List.of());
        service.reschedule(owner, 57L, today.plusDays(15), java.time.LocalTime.NOON);
        assertEquals(today.plusDays(15), notification.getReminderDate());
        Routine assigned = canonicalMetric(owner, com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT,
            today.getDayOfWeek(), java.time.LocalTime.of(5, 0));
        assigned.getReminders().clear();
        service.reschedule(owner, 57L, today.plusDays(16), java.time.LocalTime.NOON);
        assertEquals(today.plusDays(16), notification.getReminderDate());
    }

    private Routine canonicalMetric(User owner, com.jllado.weightcontrol.domain.RoutineAutomaticTrigger trigger, java.time.DayOfWeek day, java.time.LocalTime time) {
        Routine routine = routine(20L, owner); routine.setCadence(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY); routine.setAutomaticTrigger(trigger);
        RoutineReminder reminder = reminder(30L, routine); reminder.setDay(day); reminder.setReminderTime(time);
        routine.getReminders().add(reminder);
        when(routineRepository.findByUserOrderByStartDateAsc(owner)).thenReturn(List.of(routine));
        return routine;
    }

    @Test
    void rejectsPastMeasurementReschedule() {
        User user = user(1L);
        InAppNotification notification = notificationForRescheduling(52L, user, LocalDate.now(DateTimes.USER_ZONE));
        when(repository.findByIdAndUser(52L, user)).thenReturn(Optional.of(notification));

        assertThrows(BadRequestException.class, () -> service.reschedule(user, 52L, notification.getReminderDate().minusDays(1), java.time.LocalTime.NOON));
        verify(repository, never()).save(notification);
    }

    @Test
    void rejectsRescheduleForUnsupportedNotificationType() {
        User user = user(1L);
        InAppNotification notification = new InAppNotification();
        notification.setType(InAppNotificationType.APP_UPDATE);
        when(repository.findByIdAndUser(53L, user)).thenReturn(Optional.of(notification));

        assertThrows(BadRequestException.class, () -> service.reschedule(user, 53L, LocalDate.now(DateTimes.USER_ZONE), java.time.LocalTime.NOON));
        verify(repository, never()).save(notification);
    }

    @Test
    void cannotRescheduleNotificationOwnedByAnotherUser() {
        User user = user(1L);
        when(repository.findByIdAndUser(54L, user)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.reschedule(user, 54L, LocalDate.now(DateTimes.USER_ZONE), java.time.LocalTime.NOON));
        verify(repository, never()).save(any(InAppNotification.class));
    }

    @Test
    void dailyReminderCannotMoveToAnotherDate() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        InAppNotification notification = new InAppNotification();
        notification.setType(InAppNotificationType.MOOD);
        notification.setPeriod(MoodPeriod.MORNING);
        notification.setReminderDate(date);
        notification.setDeduplicationKey("MOOD:MORNING:" + date);
        when(repository.findByIdAndUser(55L, user)).thenReturn(Optional.of(notification));

        assertThrows(BadRequestException.class, () -> service.reschedule(user, 55L, date.plusDays(1), java.time.LocalTime.NOON));
        verify(repository, never()).save(notification);
    }

    private InAppNotification notificationForRescheduling(Long id, User user, LocalDate date) {
        InAppNotification notification = new InAppNotification();
        notification.setId(id);
        notification.setUser(user);
        notification.setType(InAppNotificationType.WEIGHT);
        notification.setReminderDate(date);
        notification.setAvailableAt(date.atTime(5, 0).atZone(DateTimes.USER_ZONE).toOffsetDateTime());
        notification.setDeduplicationKey("WEIGHT:" + date);
        return notification;
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
    void personalRecordNotificationShowsCardioIntervalDurationInMinutes() {
        User user = user(1L);
        RecordAchievementResponse achievement = new RecordAchievementResponse(
            "event-key", PersonalRecordMetric.CARDIO_DURATION, "Longest interval",
            PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordEventKind.IMPROVED,
            new BigDecimal("75"), new BigDecimal("60"), PersonalRecordUnit.SECONDS, LocalDate.of(2026, 8, 20),
            new PersonalRecordSubjectResponse("EXERCISE", 2L, "Walking"), null,
            new PersonalRecordSourceResponse(PersonalRecordSourceType.WORKOUT, 3L, 1, 1)
        );
        when(repository.findByUserAndDeduplicationKey(user, "PERSONAL_RECORD:event-key")).thenReturn(Optional.empty());

        service.recordPersonalRecords(user, List.of(achievement));

        var notification = org.mockito.ArgumentCaptor.forClass(InAppNotification.class);
        verify(repository).save(notification.capture());
        assertEquals("Longest interval — Walking: 1.25 minutes", notification.getValue().getMessage());
    }

    @Test
    void personalRecordNotificationOmitsTrailingZerosFromCardioIntervalMinutes() {
        User user = user(1L);
        RecordAchievementResponse achievement = new RecordAchievementResponse(
            "event-key", PersonalRecordMetric.CARDIO_DURATION, "Longest interval",
            PersonalRecordDomain.WORKOUT, PersonalRecordDirection.MAXIMUM, PersonalRecordEventKind.IMPROVED,
            new BigDecimal("2700"), new BigDecimal("1800"), PersonalRecordUnit.SECONDS, LocalDate.of(2026, 8, 20),
            new PersonalRecordSubjectResponse("EXERCISE", 2L, "Walking"), null,
            new PersonalRecordSourceResponse(PersonalRecordSourceType.WORKOUT, 3L, 1, 1)
        );
        when(repository.findByUserAndDeduplicationKey(user, "PERSONAL_RECORD:event-key")).thenReturn(Optional.empty());

        service.recordPersonalRecords(user, List.of(achievement));

        var notification = org.mockito.ArgumentCaptor.forClass(InAppNotification.class);
        verify(repository).save(notification.capture());
        assertEquals("Longest interval — Walking: 45 minutes", notification.getValue().getMessage());
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
        InAppNotification weeklySummary = notification(7L, user, InAppNotificationType.valueOf("WEEKLY_SUMMARY"), date.minusDays(7), now.minusDays(7).toOffsetDateTime());
        when(repository.findPending(
            user,
            date,
            now.toOffsetDateTime(),
            Set.of(InAppNotificationType.APP_UPDATE, InAppNotificationType.PERSONAL_RECORD, InAppNotificationType.GPT_ACTION,
                InAppNotificationType.URGE_PAUSE, InAppNotificationType.valueOf("WEEKLY_SUMMARY"))
        )).thenReturn(List.of(appUpdate, weeklySummary, routine, mood, back, weight, bloodPressure));
        when(moodRepository.existsByUserAndMoodDateAndPeriod(user, date, MoodPeriod.MIDDAY)).thenReturn(true);
        when(weightRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            user,
            DateTimes.startOfDay(com.jllado.weightcontrol.util.RoutinePeriods.start(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY, date)),
            DateTimes.startOfDay(com.jllado.weightcontrol.util.RoutinePeriods.nextStart(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY, date))
        )).thenReturn(true);

        List<InAppNotification> pending = service.findPending(user, now);

        assertEquals(List.of(appUpdate, weeklySummary, routine, back, bloodPressure), pending);
        verify(routineCheckinRepository).existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine.getRoutineReminder().getRoutine(),
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date.plusDays(1))
        );
        verify(backPainEpisodeRepository).existsByUserAndEpisodeDateAndPeriod(user, date, MoodPeriod.MIDDAY);
        verify(bloodPressureRepository).existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            user,
            DateTimes.startOfDay(com.jllado.weightcontrol.util.RoutinePeriods.start(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY, date)),
            DateTimes.startOfDay(com.jllado.weightcontrol.util.RoutinePeriods.nextStart(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY, date))
        );
    }

    @Test
    void rescheduledWeeklyRoutineNotificationUsesItsTargetWeekThroughTheCurrentDate() {
        User owner = user(1L); LocalDate saturday = LocalDate.of(2026, 10, 24);
        LocalDate monday = saturday.plusDays(2); LocalDate tuesday = saturday.plusDays(3);
        Routine routine = routine(2L, owner);
        routine.setCadence(com.jllado.weightcontrol.domain.RoutineCadence.WEEKLY);
        routine.setAutomaticTrigger(com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT);
        var notification = routineNotification(3L, owner, reminder(4L, routine), monday, DateTimes.startOfDay(monday));
        notification.setRescheduled(true); notification.setRescheduleDelivered(true);
        when(repository.findPending(org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anySet()))
            .thenReturn(List.of(notification));
        when(routineCheckinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            org.mockito.ArgumentMatchers.eq(routine), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(call -> !DateTimes.startOfDay(tuesday).isBefore(call.getArgument(1, OffsetDateTime.class))
                && DateTimes.startOfDay(tuesday).isBefore(call.getArgument(2, OffsetDateTime.class)));
        assertEquals(List.of(notification), service.findPending(owner, monday.atTime(12, 0).atZone(DateTimes.USER_ZONE)));
        assertEquals(List.of(), service.findPending(owner, tuesday.atTime(12, 0).atZone(DateTimes.USER_ZONE)));
        verify(routineCheckinRepository).existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine, DateTimes.startOfDay(saturday), DateTimes.startOfDay(monday.plusDays(1)));
        verify(routineCheckinRepository).existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine, DateTimes.startOfDay(saturday), DateTimes.startOfDay(tuesday.plusDays(1)));
        // Once that week is closed, a later read must still stop at its Saturday boundary.
        service.findPending(owner, saturday.plusWeeks(1).atTime(12, 0).atZone(DateTimes.USER_ZONE));
        verify(routineCheckinRepository).existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine, DateTimes.startOfDay(saturday), DateTimes.startOfDay(saturday.plusWeeks(1)));
    }

    @Test
    void oldMeasurementNotificationsAreSuppressedByOwnedManualWeeklyCompletions() {
        User owner = user(1L); LocalDate date = LocalDate.of(2026, 10, 28);
        ZonedDateTime now = date.atTime(12, 0).atZone(DateTimes.USER_ZONE);
        var weight = notification(1L, owner, InAppNotificationType.WEIGHT, date, now.minusHours(1).toOffsetDateTime());
        var pressure = notification(2L, owner, InAppNotificationType.BLOOD_PRESSURE, date, now.minusHours(1).toOffsetDateTime());
        when(repository.findPending(org.mockito.ArgumentMatchers.eq(owner), org.mockito.ArgumentMatchers.eq(date), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anySet()))
            .thenReturn(List.of(weight, pressure));
        for (var trigger : List.of(com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.WEIGHT_MEASUREMENT, com.jllado.weightcontrol.domain.RoutineAutomaticTrigger.BLOOD_PRESSURE_MEASUREMENT)) {
            when(routineCheckinRepository.existsByRoutineUserAndRoutineAutomaticTriggerAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(owner, trigger,
                DateTimes.startOfDay(LocalDate.of(2026,10,24)), DateTimes.startOfDay(LocalDate.of(2026,10,31)))).thenReturn(true);
        }
        assertEquals(List.of(), service.findPending(owner, now));
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
    void weeklySummaryReminderUsesOwnerAndFridayForStableDeduplication() {
        User user = user(1L);
        LocalDate friday = LocalDate.of(2026, 8, 14);
        OffsetDateTime availableAt = OffsetDateTime.parse("2026-08-18T08:30:00+02:00");
        InAppNotification dismissed = new InAppNotification();
        dismissed.setDismissedAt(OffsetDateTime.parse("2026-08-18T09:00:00+02:00"));
        String key = "WEEKLY_SUMMARY:2026-08-14";
        when(repository.findByUserAndDeduplicationKey(user, key)).thenReturn(Optional.empty(), Optional.of(dismissed));
        when(repository.save(any(InAppNotification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InAppNotification created = service.recordWeeklySummaryReminder(user, friday, availableAt).orElseThrow();
        var retried = service.recordWeeklySummaryReminder(user, friday, availableAt.plusMinutes(1));

        assertTrue(retried.isEmpty());
        var captured = org.mockito.ArgumentCaptor.forClass(InAppNotification.class);
        verify(repository).save(captured.capture());
        assertEquals(user, captured.getValue().getUser());
        assertEquals(InAppNotificationType.valueOf("WEEKLY_SUMMARY"), captured.getValue().getType());
        assertEquals(friday, captured.getValue().getReminderDate());
        assertEquals("Create your weekly summary", captured.getValue().getTitle());
        assertEquals(availableAt, captured.getValue().getAvailableAt());
        assertEquals(key, captured.getValue().getDeduplicationKey());
        assertEquals(InAppNotificationType.valueOf("WEEKLY_SUMMARY"), created.getType());
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
        when(repository.findPending(eq(user), any(LocalDate.class), any(OffsetDateTime.class), eq(Set.of(InAppNotificationType.APP_UPDATE, InAppNotificationType.PERSONAL_RECORD, InAppNotificationType.GPT_ACTION, InAppNotificationType.URGE_PAUSE, InAppNotificationType.valueOf("WEEKLY_SUMMARY")))))
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
