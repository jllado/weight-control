package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.InAppNotification;
import com.jllado.weightcontrol.domain.InAppNotificationType;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.InAppNotificationRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
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
    private InAppNotificationService service;

    @BeforeEach
    void setUp() {
        service = new InAppNotificationService(repository, routineCheckinRepository, moodRepository, backPainEpisodeRepository);
    }

    @Test
    void reminderRecordingUsesStableKeysAndUpdatesExistingNotifications() {
        User user = user(1L);
        Routine routine = routine(2L, user);
        LocalDate date = LocalDate.of(2026, 8, 20);
        OffsetDateTime firstTime = OffsetDateTime.parse("2026-08-20T07:30:00+02:00");
        OffsetDateTime secondTime = OffsetDateTime.parse("2026-08-20T07:45:00+02:00");
        InAppNotification existing = new InAppNotification();
        when(repository.findByUserAndDeduplicationKey(user, "ROUTINE:2:2026-08-20"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));

        service.recordRoutineReminder(routine, date, firstTime);
        service.recordRoutineReminder(routine, date, secondTime);

        assertEquals(InAppNotificationType.ROUTINE, existing.getType());
        assertEquals(routine, existing.getRoutine());
        assertEquals("Routine reminder", existing.getTitle());
        assertEquals("Meditation", existing.getMessage());
        assertEquals(secondTime, existing.getAvailableAt());
        verify(repository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any(InAppNotification.class));
    }

    @Test
    void pendingReturnsOnlyIncompleteNotificationsForToday() {
        User user = user(1L);
        LocalDate date = LocalDate.of(2026, 8, 20);
        ZonedDateTime now = ZonedDateTime.parse("2026-08-20T13:45:00+02:00[Europe/Madrid]");
        InAppNotification routine = routineNotification(1L, user, routine(2L, user), date, now.minusHours(6).toOffsetDateTime());
        InAppNotification mood = checkInNotification(2L, user, InAppNotificationType.MOOD, MoodPeriod.MIDDAY, date, now.minusMinutes(15).toOffsetDateTime());
        InAppNotification back = checkInNotification(3L, user, InAppNotificationType.BACK, MoodPeriod.MIDDAY, date, now.minusMinutes(15).toOffsetDateTime());
        when(repository.findByUserAndReminderDateAndDismissedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
            user,
            date,
            now.toOffsetDateTime()
        )).thenReturn(List.of(routine, mood, back));
        when(moodRepository.existsByUserAndMoodDateAndPeriod(user, date, MoodPeriod.MIDDAY)).thenReturn(true);

        List<InAppNotification> pending = service.findPending(user, now);

        assertEquals(List.of(routine, back), pending);
        verify(routineCheckinRepository).existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine.getRoutine(),
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date.plusDays(1))
        );
        verify(backPainEpisodeRepository).existsByUserAndEpisodeDateAndPeriod(user, date, MoodPeriod.MIDDAY);
    }

    @Test
    void snoozeHidesTheRoutineUntilItsNextAvailabilityOrMidnight() {
        User user = user(1L);
        Routine routine = routine(2L, user);
        LocalDate date = LocalDate.of(2026, 8, 20);
        InAppNotification notification = routineNotification(
            1L,
            user,
            routine,
            date,
            OffsetDateTime.parse("2026-08-20T07:30:00+02:00")
        );
        when(repository.findByUserAndDeduplicationKey(user, "ROUTINE:2:2026-08-20")).thenReturn(Optional.of(notification));

        OffsetDateTime nextReminderAt = OffsetDateTime.parse("2026-08-20T08:00:00+02:00");
        service.snoozeRoutineReminder(routine, date, nextReminderAt);
        assertEquals(nextReminderAt, notification.getAvailableAt());

        service.snoozeRoutineReminder(routine, date, null);
        assertEquals(DateTimes.startOfDay(date.plusDays(1)), notification.getAvailableAt());
    }

    @Test
    void dismissRequiresAnOwnedNotification() {
        User user = user(1L);
        when(repository.findByIdAndUser(10L, user)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.dismiss(user, 10L));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
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
        Routine routine,
        LocalDate date,
        OffsetDateTime availableAt
    ) {
        InAppNotification notification = notification(id, user, InAppNotificationType.ROUTINE, date, availableAt);
        notification.setRoutine(routine);
        return notification;
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
