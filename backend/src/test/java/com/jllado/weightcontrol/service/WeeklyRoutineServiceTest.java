package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jllado.weightcontrol.api.dto.RoutineDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.*;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.RoutinePeriods;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class WeeklyRoutineServiceTest {
    final RoutineRepository repository = mock(RoutineRepository.class);
    final RoutineCheckinRepository checkins = mock(RoutineCheckinRepository.class);
    final RoutineAutomationService automation = mock(RoutineAutomationService.class);
    final RoutineService service = new RoutineService(repository, checkins, mock(InAppNotificationService.class), automation);

    @Test void weeklyCreationUsesThisWeekAndImmutableCadenceWhileDailyKeepsTheAnchor() {
        User owner = new User(); owner.setId(1L); owner.setDashboardAnchorDate(LocalDate.of(2020,1,1));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        Routine daily = service.create(owner, new RoutineRequest("Daily", Set.of(RoutineType.MIND), List.of(), true));
        assertEquals(RoutineCadence.DAILY, daily.getCadence());
        assertEquals(owner.getDashboardAnchorDate(), DateTimes.toLocalDate(daily.getStartDate()));
        Routine weekly = service.create(owner, request(null, List.of()));
        assertEquals(RoutinePeriods.start(RoutineCadence.WEEKLY, LocalDate.now(DateTimes.USER_ZONE)), DateTimes.toLocalDate(weekly.getStartDate()));
        weekly.setId(2L); when(repository.findById(2L)).thenReturn(Optional.of(weekly));
        service.update(owner, 2L, new RoutineRequest("Weekly", Set.of(RoutineType.MIND), null, true));
        assertEquals(RoutineCadence.WEEKLY, weekly.getCadence());
        assertThrows(BadRequestException.class, () -> service.update(owner, 2L, new RoutineRequest("Daily", Set.of(RoutineType.MIND), List.of(), true, null, null, RoutineCadence.DAILY, null)));
    }

    @Test void weeklyRemindersAreUniquePairsAndUnchangedPairsRetainSnoozes() {
        User owner = new User(); owner.setId(1L);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        var monday = new WeeklyReminderRequest(DayOfWeek.MONDAY, LocalTime.of(8,0));
        var friday = new WeeklyReminderRequest(DayOfWeek.FRIDAY, LocalTime.of(8,0));
        Routine weekly = service.create(owner, request(null, List.of(monday, friday)));
        assertEquals(2, weekly.getReminders().size());
        weekly.setId(2L); when(repository.findById(2L)).thenReturn(Optional.of(weekly));
        var reminder = weekly.getReminders().iterator().next();
        reminder.setReminderSnoozedUntil(OffsetDateTime.parse("2026-10-26T08:30+01:00"));
        service.update(owner, 2L, request(null, List.of(monday, friday)));
        assertNotNull(reminder.getReminderSnoozedUntil());
        assertThrows(BadRequestException.class, () -> service.update(owner, 2L, request(null, List.of(monday, new WeeklyReminderRequest(DayOfWeek.MONDAY, LocalTime.of(8,0,59))))));
    }

    @Test void summaryCountsDistinctConsecutiveWeeksAndKeepsActualLastDate() {
        Routine weekly = new Routine(); weekly.setCadence(RoutineCadence.WEEKLY);
        var dates = List.of("2026-10-11", "2026-10-16", "2026-10-19", "2026-10-28");
        RoutineService.rebuildSummary(weekly, dates.stream().map(date -> checkin(weekly, LocalDate.parse(date))).toList());
        assertEquals(3, weekly.getCurrentStrike()); assertEquals(3, weekly.getBestStrike());
        assertEquals(LocalDate.of(2026,10,28), DateTimes.toLocalDate(weekly.getLastTimeDate()));
    }

    @Test void undoResolvesOnlySelectedWeeksManualEvidenceAndKeepsPausedAutomaticEvidence() {
        User owner = new User(); owner.setId(1L);
        Routine weekly = new Routine(); weekly.setId(2L); weekly.setUser(owner); weekly.setCadence(RoutineCadence.WEEKLY);
        LocalDate selected = LocalDate.of(2026,10,28);
        RoutineCheckin combined = checkin(weekly, selected.minusDays(1));
        RoutineCheckin manual = checkin(weekly, selected);
        RoutineCheckin automatic = checkin(weekly, selected.minusDays(2)); automatic.setManualCompletion(false);
        when(repository.findById(2L)).thenReturn(Optional.of(weekly));
        when(checkins.findByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(weekly, DateTimes.startOfDay(LocalDate.of(2026,10,24)), DateTimes.startOfDay(selected.plusDays(1))))
            .thenReturn(List.of(automatic, combined, manual));
        when(automation.hasEvidence(weekly, selected.minusDays(1))).thenReturn(true);
        when(checkins.findByRoutineOrderByCheckedAtAsc(weekly)).thenReturn(List.of(automatic, combined));
        service.undoCheckin(owner, 2L, DateTimes.startOfDay(selected));
        assertFalse(combined.isManualCompletion());
        verify(checkins).delete(manual); verify(checkins, never()).delete(automatic); verify(checkins, never()).delete(combined);
        verify(automation, never()).reconcile(any());
        assertEquals(1, weekly.getBestStrike());
        User other = new User(); other.setId(3L);
        assertThrows(NotFoundException.class, () -> service.undoCheckin(other, 2L, DateTimes.startOfDay(selected)));
    }

    @Test void pausedDailyUndoRetainsTheEstablishedDeleteWithoutReconciliationBehavior() {
        User owner = new User(); owner.setId(1L);
        Routine daily = new Routine(); daily.setId(2L); daily.setUser(owner);
        daily.setAutomaticTrigger(RoutineAutomaticTrigger.FRUIT_MEAL); daily.setAutomaticCompletionEnabled(false);
        var date = LocalDate.of(2026,10,28);
        RoutineCheckin automatic = checkin(daily, date); automatic.setManualCompletion(false);
        when(repository.findById(2L)).thenReturn(Optional.of(daily));
        when(checkins.findByRoutineAndCheckedAt(daily, automatic.getCheckedAt())).thenReturn(Optional.of(automatic));
        service.undoCheckin(owner, 2L, automatic.getCheckedAt());
        verify(checkins).delete(automatic);
        verify(automation, never()).reconcile(any());
        verify(automation, never()).hasEvidence(any(), any());
        assertEquals(0, daily.getCurrentStrike()); assertEquals(0, daily.getBestStrike());
    }

    private RoutineRequest request(RoutineAutomaticTrigger trigger, List<WeeklyReminderRequest> reminders) {
        return new RoutineRequest("Weekly", Set.of(RoutineType.MIND), null, true, trigger, null, RoutineCadence.WEEKLY, reminders);
    }
    private RoutineCheckin checkin(Routine routine, LocalDate date) {
        var checkin = new RoutineCheckin(); checkin.setRoutine(routine); checkin.setCheckedAt(DateTimes.startOfDay(date)); return checkin;
    }
}
