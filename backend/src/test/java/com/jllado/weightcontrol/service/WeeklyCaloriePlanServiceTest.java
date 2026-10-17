package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.DayPlanRequest;
import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.WeeklyCaloriePlanRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyCaloriePlan;
import com.jllado.weightcontrol.repository.WeeklyCaloriePlanRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WeeklyCaloriePlanServiceTest {

    @Mock private WeeklyCaloriePlanRepository repository;
    @InjectMocks private WeeklyCaloriePlanService service;

    @Test
    void savesSevenDatedCaloriesForTheOwningUser() {
        User user = user(1L);
        LocalDate saturday = LocalDate.of(2026, 8, 15);
        WeeklyCaloriePlanRequest request = request(saturday, null, 3000);

        service.replace(user, request);

        ArgumentCaptor<WeeklyCaloriePlan> saved = ArgumentCaptor.forClass(WeeklyCaloriePlan.class);
        verify(repository).save(saved.capture());
        assertEquals(user, saved.getValue().getUser());
        assertEquals(saturday, saved.getValue().getWeekStart());
        assertEquals(List.of(3000, 2000, 2000, 2000, 2000, 2000, 2000), saved.getValue().getCalories());
    }

    @Test
    void rejectsAWeekThatDoesNotStartOnSaturday() {
        assertThrows(BadRequestException.class, () -> service.replace(user(1L),
            request(LocalDate.of(2026, 8, 16), null, 3000)));
    }

    @Test
    void rejectsDuplicateOrNoncontiguousDatesAndNegativeCalories() {
        LocalDate start = LocalDate.of(2026, 8, 15);
        var duplicateDates = new WeeklyCaloriePlanRequest(start, null, List.of(
            new DayPlanRequest(start, 2000), new DayPlanRequest(start, 2000), new DayPlanRequest(start.plusDays(2), 2000),
            new DayPlanRequest(start.plusDays(3), 2000), new DayPlanRequest(start.plusDays(4), 2000),
            new DayPlanRequest(start.plusDays(5), 2000), new DayPlanRequest(start.plusDays(6), 2000)));
        assertThrows(BadRequestException.class, () -> service.replace(user(1L), duplicateDates));
        assertThrows(BadRequestException.class, () -> service.replace(user(1L), request(start, null, -1)));
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
        saved.setCalories(List.of(3000, 2000, 2000, 2000, 2000, 2000, 2000));
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
        verify(repository).findByUserAndWeekStart(user, saturday);
    }

    @Test
    void rejectsAStaleRevisionInsteadOfReplacingAnotherTabSave() {
        User user = user(1L);
        WeeklyCaloriePlan existing = new WeeklyCaloriePlan();
        existing.setUser(user);
        existing.setWeekStart(LocalDate.of(2026, 8, 15));
        existing.setRevision(4L);
        when(repository.findByUserAndWeekStart(user, existing.getWeekStart())).thenReturn(Optional.of(existing));

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

    private User user(long id) { User user = new User(); user.setId(id); return user; }
}
