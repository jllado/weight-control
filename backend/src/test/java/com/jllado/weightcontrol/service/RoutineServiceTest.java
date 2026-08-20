package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.RoutineType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

    @Mock
    private RoutineRepository repository;

    @Mock
    private RoutineCheckinRepository checkinRepository;

    @Mock
    private InAppNotificationService inAppNotificationService;

    @InjectMocks
    private RoutineService service;

    @Test
    void updateStoresTheOptionalReminderAtMinutePrecision() {
        User user = new User();
        user.setId(1L);
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        when(repository.findById(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);

        Routine updated = service.update(
            user,
            routine.getId(),
            new RoutineRequest("Meditation", Set.of(RoutineType.MIND), LocalTime.of(13, 7, 45))
        );

        assertEquals(LocalTime.of(13, 7), updated.getReminderTime());
    }

    @Test
    void updateClearsTheOptionalReminder() {
        User user = new User();
        user.setId(1L);
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        routine.setReminderTime(LocalTime.of(13, 7));
        routine.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-13T14:00:00+02:00"));
        when(repository.findById(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);

        Routine updated = service.update(
            user,
            routine.getId(),
            new RoutineRequest("Meditation", Set.of(RoutineType.MIND), null)
        );

        assertNull(updated.getReminderTime());
        assertNull(updated.getReminderSnoozedUntil());
    }

    @Test
    void snoozeReminderStoresAndReplacesThePendingReminder() {
        User user = new User();
        user.setId(1L);
        Routine routine = activeRoutine(user);
        ZonedDateTime firstRequest = ZonedDateTime.parse("2026-08-13T08:00:30+02:00[Europe/Madrid]");
        ZonedDateTime secondRequest = ZonedDateTime.parse("2026-08-13T08:05:00+02:00[Europe/Madrid]");
        when(repository.findByIdForUpdate(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);

        OffsetDateTime firstReminder = service.snoozeReminder(user, routine.getId(), 15, firstRequest);
        OffsetDateTime secondReminder = service.snoozeReminder(user, routine.getId(), 30, secondRequest);

        assertEquals(firstRequest.plusMinutes(15).toOffsetDateTime(), firstReminder);
        assertEquals(secondRequest.plusMinutes(30).toOffsetDateTime(), secondReminder);
        assertEquals(secondReminder, routine.getReminderSnoozedUntil());
        verify(repository, times(2)).save(routine);
        verify(inAppNotificationService).snoozeRoutineReminder(routine, firstRequest.toLocalDate(), firstReminder);
        verify(inAppNotificationService).snoozeRoutineReminder(routine, secondRequest.toLocalDate(), secondReminder);
    }

    @Test
    void snoozeReminderExpiresWhenTheDelayCrossesMidnight() {
        User user = new User();
        user.setId(1L);
        Routine routine = activeRoutine(user);
        routine.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-13T23:45:00+02:00"));
        ZonedDateTime now = ZonedDateTime.parse("2026-08-13T23:30:00+02:00[Europe/Madrid]");
        when(repository.findByIdForUpdate(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);

        OffsetDateTime nextReminderAt = service.snoozeReminder(user, routine.getId(), 60, now);

        assertNull(nextReminderAt);
        assertNull(routine.getReminderSnoozedUntil());
        verify(inAppNotificationService).snoozeRoutineReminder(routine, now.toLocalDate(), null);
    }

    @Test
    void snoozeReminderRejectsUnsupportedDelaysAndInactiveReminders() {
        User user = new User();
        user.setId(1L);
        Routine routine = activeRoutine(user);
        ZonedDateTime now = ZonedDateTime.parse("2026-08-13T08:00:00+02:00[Europe/Madrid]");
        when(repository.findByIdForUpdate(routine.getId())).thenReturn(Optional.of(routine));
        when(checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(any(), any(), any())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.snoozeReminder(user, routine.getId(), 10, now));
        assertThrows(BadRequestException.class, () -> service.snoozeReminder(user, routine.getId(), 15, now));

        verify(repository, never()).save(any());
    }

    @Test
    void snoozeReminderRejectsAnotherUsersRoutine() {
        User owner = new User();
        owner.setId(1L);
        User anotherUser = new User();
        anotherUser.setId(2L);
        Routine routine = activeRoutine(owner);
        ZonedDateTime now = ZonedDateTime.parse("2026-08-13T08:00:00+02:00[Europe/Madrid]");
        when(repository.findByIdForUpdate(routine.getId())).thenReturn(Optional.of(routine));

        assertThrows(NotFoundException.class, () -> service.snoozeReminder(anotherUser, routine.getId(), 15, now));

        verify(repository, never()).save(any());
    }

    @Test
    void checkinCreatesCompletionsOnDifferentMadridDays() {
        User user = new User();
        user.setId(1L);
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        routine.setCurrentStrike(0);
        routine.setBestStrike(0);
        routine.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-12T19:00:00+02:00"));
        OffsetDateTime first = OffsetDateTime.parse("2026-08-12T07:30:00+02:00");
        OffsetDateTime second = OffsetDateTime.parse("2026-08-13T07:30:00+02:00");
        LocalDate firstDate = LocalDate.of(2026, 8, 12);
        when(repository.findByIdForUpdate(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);

        service.checkin(user, routine.getId(), first);
        service.checkin(user, routine.getId(), second);

        verify(checkinRepository).existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine,
            DateTimes.startOfDay(firstDate),
            DateTimes.startOfDay(firstDate.plusDays(1))
        );
        verify(checkinRepository, times(2)).save(any(RoutineCheckin.class));
        assertEquals(2, routine.getCurrentStrike());
        assertNull(routine.getReminderSnoozedUntil());
    }

    @Test
    void checkinReturnsAnExistingSameDayCompletionWithoutCreatingAnother() {
        User user = new User();
        user.setId(1L);
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        routine.setCurrentStrike(4);
        routine.setBestStrike(7);
        OffsetDateTime checkedAt = OffsetDateTime.parse("2026-08-12T18:30:00+02:00");
        LocalDate checkedDate = LocalDate.of(2026, 8, 12);
        when(repository.findByIdForUpdate(routine.getId())).thenReturn(Optional.of(routine));
        when(checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine,
            DateTimes.startOfDay(checkedDate),
            DateTimes.startOfDay(checkedDate.plusDays(1))
        )).thenReturn(true);

        Routine result = service.checkin(user, routine.getId(), checkedAt);

        assertEquals(routine, result);
        verify(checkinRepository, never()).save(any());
        verify(repository, never()).save(any());
        assertEquals(4, routine.getCurrentStrike());
    }

    @Test
    void undoCheckinRebuildsSummaryFromRemainingCheckins() {
        User user = new User();
        user.setId(1L);

        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        routine.setCurrentStrike(1);
        routine.setBestStrike(2);
        routine.setLastTimeDate(OffsetDateTime.parse("2026-06-12T00:00:00+02:00"));

        OffsetDateTime first = OffsetDateTime.parse("2026-06-10T00:00:00+02:00");
        OffsetDateTime second = OffsetDateTime.parse("2026-06-11T00:00:00+02:00");
        OffsetDateTime removed = OffsetDateTime.parse("2026-06-12T00:00:00+02:00");

        RoutineCheckin removedCheckin = new RoutineCheckin();
        removedCheckin.setRoutine(routine);
        removedCheckin.setCheckedAt(removed);

        RoutineCheckin firstCheckin = new RoutineCheckin();
        firstCheckin.setRoutine(routine);
        firstCheckin.setCheckedAt(first);

        RoutineCheckin secondCheckin = new RoutineCheckin();
        secondCheckin.setRoutine(routine);
        secondCheckin.setCheckedAt(second);

        when(repository.findById(routine.getId())).thenReturn(Optional.of(routine));
        when(checkinRepository.findByRoutineAndCheckedAt(routine, removed)).thenReturn(Optional.of(removedCheckin));
        when(checkinRepository.findByRoutineOrderByCheckedAtAsc(routine)).thenReturn(List.of(firstCheckin, secondCheckin));
        when(repository.save(routine)).thenReturn(routine);

        service.undoCheckin(user, routine.getId(), removed);

        verify(checkinRepository).delete(removedCheckin);
        ArgumentCaptor<Routine> savedRoutine = ArgumentCaptor.forClass(Routine.class);
        verify(repository).save(savedRoutine.capture());
        assertEquals(2, savedRoutine.getValue().getCurrentStrike());
        assertEquals(2, savedRoutine.getValue().getBestStrike());
        assertEquals(second, savedRoutine.getValue().getLastTimeDate());
    }

    private static Routine activeRoutine(User user) {
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        routine.setStartDate(OffsetDateTime.parse("2026-08-01T00:00:00+02:00"));
        routine.setReminderTime(LocalTime.of(7, 30));
        return routine;
    }
}
