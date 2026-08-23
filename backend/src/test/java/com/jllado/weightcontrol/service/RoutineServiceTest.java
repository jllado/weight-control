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
import com.jllado.weightcontrol.domain.RoutineReminder;
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
    void updateStoresSeveralUniqueRemindersAtMinutePrecision() {
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
            new RoutineRequest(
                "Meditation",
                Set.of(RoutineType.MIND),
                List.of(LocalTime.of(18, 30, 45), LocalTime.of(13, 7, 45))
            )
        );

        assertEquals(
            List.of(LocalTime.of(13, 7), LocalTime.of(18, 30)),
            updated.getReminders().stream().map(RoutineReminder::getReminderTime).toList()
        );
    }

    @Test
    void updatePreservesUnchangedRemindersAndRemovesDeletedReminders() {
        User user = new User();
        user.setId(1L);
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        RoutineReminder kept = reminder(3L, routine, LocalTime.of(13, 7));
        kept.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-13T14:00:00+02:00"));
        routine.getReminders().add(kept);
        routine.getReminders().add(reminder(4L, routine, LocalTime.of(18, 30)));
        when(repository.findById(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);

        Routine updated = service.update(
            user,
            routine.getId(),
            new RoutineRequest("Meditation", Set.of(RoutineType.MIND), List.of(LocalTime.of(13, 7), LocalTime.of(20, 0)))
        );

        assertEquals(2, updated.getReminders().size());
        assertEquals(kept, updated.getReminders().stream().filter(reminder -> reminder.getReminderTime().equals(LocalTime.of(13, 7))).findFirst().orElseThrow());
        assertEquals(OffsetDateTime.parse("2026-08-13T14:00:00+02:00"), kept.getReminderSnoozedUntil());
    }

    @Test
    void updateRejectsDuplicateReminderMinutes() {
        User user = new User();
        user.setId(1L);
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        when(repository.findById(routine.getId())).thenReturn(Optional.of(routine));

        assertThrows(
            BadRequestException.class,
            () -> service.update(
                user,
                routine.getId(),
                new RoutineRequest("Meditation", Set.of(RoutineType.MIND), List.of(LocalTime.of(13, 7, 10), LocalTime.of(13, 7, 45)))
            )
        );

        verify(repository, never()).save(any());
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
        RoutineReminder reminder = routine.getReminders().iterator().next();
        OffsetDateTime firstReminder = service.snoozeReminder(user, routine.getId(), reminder.getId(), 15, firstRequest);
        OffsetDateTime secondReminder = service.snoozeReminder(user, routine.getId(), reminder.getId(), 30, secondRequest);

        assertEquals(firstRequest.plusMinutes(15).toOffsetDateTime(), firstReminder);
        assertEquals(secondRequest.plusMinutes(30).toOffsetDateTime(), secondReminder);
        assertEquals(secondReminder, reminder.getReminderSnoozedUntil());
        verify(repository, times(2)).save(routine);
        verify(inAppNotificationService).snoozeRoutineReminder(reminder, firstRequest.toLocalDate(), firstReminder);
        verify(inAppNotificationService).snoozeRoutineReminder(reminder, secondRequest.toLocalDate(), secondReminder);
    }

    @Test
    void snoozeReminderExpiresWhenTheDelayCrossesMidnight() {
        User user = new User();
        user.setId(1L);
        Routine routine = activeRoutine(user);
        RoutineReminder reminder = routine.getReminders().iterator().next();
        reminder.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-13T23:45:00+02:00"));
        ZonedDateTime now = ZonedDateTime.parse("2026-08-13T23:30:00+02:00[Europe/Madrid]");
        when(repository.findByIdForUpdate(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);

        OffsetDateTime nextReminderAt = service.snoozeReminder(user, routine.getId(), reminder.getId(), 60, now);

        assertNull(nextReminderAt);
        assertNull(reminder.getReminderSnoozedUntil());
        verify(inAppNotificationService).snoozeRoutineReminder(reminder, now.toLocalDate(), null);
    }

    @Test
    void snoozeReminderRejectsUnsupportedDelaysAndInactiveReminders() {
        User user = new User();
        user.setId(1L);
        Routine routine = activeRoutine(user);
        ZonedDateTime now = ZonedDateTime.parse("2026-08-13T08:00:00+02:00[Europe/Madrid]");
        when(repository.findByIdForUpdate(routine.getId())).thenReturn(Optional.of(routine));
        when(checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(any(), any(), any())).thenReturn(true);

        Long reminderId = routine.getReminders().iterator().next().getId();
        assertThrows(BadRequestException.class, () -> service.snoozeReminder(user, routine.getId(), reminderId, 10, now));
        assertThrows(BadRequestException.class, () -> service.snoozeReminder(user, routine.getId(), reminderId, 15, now));

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

        Long reminderId = routine.getReminders().iterator().next().getId();
        assertThrows(NotFoundException.class, () -> service.snoozeReminder(anotherUser, routine.getId(), reminderId, 15, now));

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
        RoutineReminder morning = reminder(3L, routine, LocalTime.of(7, 30));
        morning.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-12T19:00:00+02:00"));
        RoutineReminder evening = reminder(4L, routine, LocalTime.of(18, 30));
        evening.setReminderSnoozedUntil(OffsetDateTime.parse("2026-08-12T20:00:00+02:00"));
        routine.getReminders().add(morning);
        routine.getReminders().add(evening);
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
        verify(checkinRepository, never()).findByRoutineOrderByCheckedAtAsc(routine);
        assertEquals(2, routine.getCurrentStrike());
        assertNull(morning.getReminderSnoozedUntil());
        assertNull(evening.getReminderSnoozedUntil());
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
    void checkinRebuildsSummaryWhenAddingABackdatedCompletion() {
        User user = new User();
        user.setId(1L);
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        routine.setCurrentStrike(1);
        routine.setBestStrike(1);
        routine.setLastTimeDate(OffsetDateTime.parse("2026-08-13T07:30:00+02:00"));
        OffsetDateTime backdated = OffsetDateTime.parse("2026-08-12T07:30:00+02:00");
        RoutineCheckin first = checkin(routine, backdated);
        RoutineCheckin second = checkin(routine, routine.getLastTimeDate());
        when(repository.findByIdForUpdate(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);
        when(checkinRepository.findByRoutineOrderByCheckedAtAsc(routine)).thenReturn(List.of(first, second));

        service.checkin(user, routine.getId(), backdated);

        verify(checkinRepository).findByRoutineOrderByCheckedAtAsc(routine);
        assertEquals(2, routine.getCurrentStrike());
        assertEquals(2, routine.getBestStrike());
        assertEquals(second.getCheckedAt(), routine.getLastTimeDate());
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
        routine.getReminders().add(reminder(3L, routine, LocalTime.of(7, 30)));
        return routine;
    }

    private static RoutineReminder reminder(Long id, Routine routine, LocalTime time) {
        RoutineReminder reminder = new RoutineReminder();
        reminder.setId(id);
        reminder.setRoutine(routine);
        reminder.setReminderTime(time);
        return reminder;
    }

    private static RoutineCheckin checkin(Routine routine, OffsetDateTime checkedAt) {
        RoutineCheckin checkin = new RoutineCheckin();
        checkin.setRoutine(routine);
        checkin.setCheckedAt(checkedAt);
        return checkin;
    }
}
