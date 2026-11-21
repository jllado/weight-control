package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.PlanningWeekChange;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.PlanningWeekChangeRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlanningWeekServiceTest {
    private final User user = new User();

    @Test
    void anAccountWithoutChangesHasSaturdayWeeksAcrossTheYearBoundary() {
        var week = PlanningWeekService.period(user, LocalDate.parse("2026-01-01"));
        assertEquals(LocalDate.parse("2025-12-27"), week.start());
        assertEquals(LocalDate.parse("2026-01-02"), week.end());
        assertFalse(week.transition());
    }

    @Test
    void selectedDayStartsStrictlyInTheFutureAndPendingChoicesCanBeReplacedOrCancelled() {
        var changes = mock(PlanningWeekChangeRepository.class);
        when(changes.save(any(PlanningWeekChange.class))).thenAnswer(call -> call.getArgument(0));
        var service = new PlanningWeekService(changes);
        LocalDate monday = LocalDate.parse("2026-11-23");

        service.schedule(user, DayOfWeek.MONDAY, monday, monday.plusWeeks(1));
        var first = user.getPlanningWeekChanges().getFirst();
        assertEquals(monday.plusWeeks(1), first.getEffectiveDate());

        service.schedule(user, DayOfWeek.WEDNESDAY, monday, monday.plusDays(2));
        assertEquals(monday.plusDays(2), first.getEffectiveDate());
        service.schedule(user, DayOfWeek.WEDNESDAY, monday.plusDays(1), monday.plusDays(2));
        assertEquals(1, user.getPlanningWeekChanges().size());
        service.schedule(user, DayOfWeek.SATURDAY, monday.plusDays(1), null);
        assertEquals(0, user.getPlanningWeekChanges().size());

        service.schedule(user, DayOfWeek.WEDNESDAY, monday, monday.plusDays(2));
        service.schedule(user, DayOfWeek.MONDAY, monday.plusDays(2), monday.plusWeeks(1));
        assertEquals(2, user.getPlanningWeekChanges().size());
        assertEquals(monday.plusDays(2), user.getPlanningWeekChanges().getFirst().getEffectiveDate());
        assertEquals(monday.plusWeeks(1), user.getPlanningWeekChanges().getLast().getEffectiveDate());
    }

    @Test
    void requestThatCrossesMidnightCannotSaveAnUnseenEffectiveDate() {
        var changes = mock(PlanningWeekChangeRepository.class);
        var service = new PlanningWeekService(changes);
        LocalDate friday = LocalDate.of(2027, 1, 1);
        var conflict = assertThrows(ResponseStatusException.class, () -> service.schedule(user, DayOfWeek.FRIDAY, friday, friday));
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals(0, user.getPlanningWeekChanges().size());
        verifyNoInteractions(changes);
    }

    @Test
    void everyDateBelongsToOnePeriodAcrossSuccessiveChanges() {
        user.getPlanningWeekChanges().add(new PlanningWeekChange(user, LocalDate.parse("2026-11-23"))); // Monday
        user.getPlanningWeekChanges().add(new PlanningWeekChange(user, LocalDate.parse("2026-12-01"))); // Tuesday

        LocalDate start = LocalDate.parse("2026-11-14");
        for (int length : new int[]{7, 2, 7, 1, 7, 7}) {
            LocalDate end = start.plusDays(length - 1);
            for (LocalDate date : start.datesUntil(end.plusDays(1)).toList()) {
                var week = PlanningWeekService.period(user, date);
                assertEquals(start, week.start());
                assertEquals(end, week.end());
                assertEquals(length < 7, week.transition());
            }
            start = end.plusDays(1);
        }
    }
}
