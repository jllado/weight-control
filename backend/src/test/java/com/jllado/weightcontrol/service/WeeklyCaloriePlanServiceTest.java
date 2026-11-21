package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.DayPlanRequest;
import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.WeeklyCaloriePlanRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyCaloriePlan;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeeklyCaloriePlanRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WeeklyCaloriePlanServiceTest {

    @Mock private WeeklyCaloriePlanRepository repository;
    @Mock private UserRepository users;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    @InjectMocks private WeeklyCaloriePlanService service;

    @Test
    void savesSevenDatedCaloriesForTheOwningUser() {
        User user = user(1L);
        LocalDate saturday = LocalDate.of(2026, 8, 15);
        WeeklyCaloriePlanRequest request = request(saturday, null, 3000);

        lock(user);
        doAnswer(call -> {
            Iterable<WeeklyCaloriePlan> rows = call.getArgument(0);
            var row = rows.iterator().next();
            assertEquals(user, row.getUser());
            assertEquals(saturday, row.getWeekStart());
            row.setRevision(0L);
            return List.of(row);
        }).when(repository).saveAllAndFlush(any());

        var saved = service.replace(user, request);
        assertEquals(0L, saved.revision());
        verify(events).publishEvent(new WeeklyCaloriePlanService.CaloriePlanSaved(user, saturday));
        assertEquals(List.of(3000, 2000, 2000, 2000, 2000, 2000, 2000), saved.days().stream().map(day -> day.calories()).toList());
    }

    @Test
    void rejectsAWeekThatDoesNotStartOnSaturday() {
        User user = user(1L);
        lock(user);
        assertThrows(BadRequestException.class, () -> service.replace(user,
            request(LocalDate.of(2026, 8, 16), null, 3000)));
    }

    @Test
    void rejectsDuplicateOrNoncontiguousDatesAndNegativeCalories() {
        LocalDate start = LocalDate.of(2026, 8, 15);
        User user = user(1L);
        lock(user);
        var duplicateDates = new WeeklyCaloriePlanRequest(start, null, List.of(
            new DayPlanRequest(start, 2000), new DayPlanRequest(start, 2000), new DayPlanRequest(start.plusDays(2), 2000),
            new DayPlanRequest(start.plusDays(3), 2000), new DayPlanRequest(start.plusDays(4), 2000),
            new DayPlanRequest(start.plusDays(5), 2000), new DayPlanRequest(start.plusDays(6), 2000)));
        assertThrows(BadRequestException.class, () -> service.replace(user, duplicateDates));
        assertThrows(BadRequestException.class, () -> service.replace(user, request(start, null, -1)));
        verifyNoInteractions(repository);
    }

    @Test
    void effectiveTargetsUseSavedDatesAndWeekdayDefaultsForUnplannedWeeks() {
        User user = user(1L);
        user.setTypicalCaloriesSaturday(2100);
        user.setTypicalCaloriesSunday(2200);
        LocalDate start = LocalDate.of(2026, 8, 15);
        WeeklyCaloriePlan saved = new WeeklyCaloriePlan();
        saved.setUser(user);
        saved.setWeekStart(start);
        saved.setSaturdayCalories(3000);
        saved.setSundayCalories(2000);
        when(repository.findByUserAndWeekStartBetweenOrderByWeekStartAsc(user, start, start.plusWeeks(1)))
            .thenReturn(List.of(saved));

        var targets = service.effectiveTargets(user, start, start.plusDays(8));

        assertEquals(3000, targets.get(0).calories());
        assertEquals(2000, targets.get(1).calories());
        assertEquals(2100, targets.get(7).calories());
        assertEquals(2200, targets.get(8).calories());
    }

    @Test
    void readsOnlyTheRequestedAccountsWeek() {
        User user = user(1L);
        LocalDate saturday = LocalDate.of(2026, 8, 15);
        service.find(user, saturday);
        verify(repository).findByUserAndWeekStartBetweenOrderByWeekStartAsc(user, saturday, saturday);
    }

    @Test
    void rejectsAStaleRevisionInsteadOfReplacingAnotherTabSave() {
        User user = user(1L);
        WeeklyCaloriePlan existing = new WeeklyCaloriePlan();
        existing.setUser(user);
        existing.setWeekStart(LocalDate.of(2026, 8, 15));
        existing.setRevision(4L);
        existing.setSaturdayCalories(0);
        lock(user);
        when(repository.findByUserAndWeekStartBetweenOrderByWeekStartAsc(user, existing.getWeekStart(), existing.getWeekStart()))
            .thenReturn(List.of(existing));

        assertThrows(ResponseStatusException.class, () -> service.replace(user,
            request(existing.getWeekStart(), 3L, 3000)));
    }

    private WeeklyCaloriePlanRequest request(LocalDate start, Long revision, int saturdayCalories) {
        return new WeeklyCaloriePlanRequest(start, revision, List.of(
            new DayPlanRequest(start, saturdayCalories), new DayPlanRequest(start.plusDays(1), 2000),
            new DayPlanRequest(start.plusDays(2), 2000), new DayPlanRequest(start.plusDays(3), 2000),
            new DayPlanRequest(start.plusDays(4), 2000), new DayPlanRequest(start.plusDays(5), 2000),
            new DayPlanRequest(start.plusDays(6), 2000)));
    }

    private void lock(User user) {
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    }

    private User user(long id) { User user = new User(); user.setId(id); return user; }
}
