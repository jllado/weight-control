package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.CoachFastingPeriodRequest;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodRequest;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.FastingPeriodSource;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.FastingPeriodRepository;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FastingPeriodServiceTest {

    @Mock
    private FastingPeriodRepository repository;

    @Mock
    private MealRepository mealRepository;

    @Mock
    private RoutineAutomationService routineAutomationService;

    @InjectMocks
    private FastingPeriodService service;

    private MockedStatic<Clock> clock;

    @BeforeEach
    void fixCurrentTimeForRelativeMealFixtures() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), DateTimes.USER_ZONE);
        clock = mockStatic(Clock.class, CALLS_REAL_METHODS);
        clock.when(() -> Clock.system(DateTimes.USER_ZONE)).thenReturn(fixed);
    }

    @AfterEach
    void restoreSystemClock() {
        clock.close();
    }

    @Test
    void createStoresCompletedNonOverlappingPeriod() {
        User user = user(1L);
        OffsetDateTime end = OffsetDateTime.now(DateTimes.USER_ZONE).minusHours(1);
        FastingPeriodRequest request = new FastingPeriodRequest(end.minusHours(16), end, "Overnight fast");

        service.create(user, request);

        ArgumentCaptor<FastingPeriod> period = ArgumentCaptor.forClass(FastingPeriod.class);
        verify(repository).save(period.capture());
        assertEquals(request.startTime(), period.getValue().getStartTime());
        assertEquals(request.endTime(), period.getValue().getEndTime());
        assertEquals("Overnight fast", period.getValue().getNotes());
    }

    @Test
    void createRejectsInvalidFutureAndOverlappingPeriods() {
        User user = user(1L);
        OffsetDateTime now = OffsetDateTime.now(DateTimes.USER_ZONE);
        FastingPeriodRequest reversed = new FastingPeriodRequest(now.minusHours(1), now.minusHours(2), null);
        FastingPeriodRequest future = new FastingPeriodRequest(now.minusHours(1), now.plusMinutes(1), null);
        FastingPeriodRequest overlap = new FastingPeriodRequest(now.minusHours(16), now.minusHours(1), null);
        when(repository.existsOverlapping(user, overlap.startTime(), overlap.endTime(), null)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.create(user, reversed));
        assertThrows(BadRequestException.class, () -> service.create(user, future));
        assertThrows(BadRequestException.class, () -> service.create(user, overlap));
    }

    @Test
    void writesRequireOwnershipAndCoachConfirmation() {
        User user = user(1L);
        FastingPeriod foreign = new FastingPeriod();
        foreign.setId(10L);
        foreign.setUser(user(2L));
        when(repository.findById(10L)).thenReturn(Optional.of(foreign));
        OffsetDateTime end = OffsetDateTime.now(DateTimes.USER_ZONE).minusHours(1);
        CoachFastingPeriodRequest request = new CoachFastingPeriodRequest(end.minusHours(16), end, null, false);

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
        assertThrows(BadRequestException.class, () -> service.createConfirmed(user, request));
        assertThrows(BadRequestException.class, () -> service.deleteConfirmed(user, 10L, false));
    }

    @Test
    void findsCurrentAutomaticPeriodAfterEightHours() {
        User user = user(1L);
        OffsetDateTime start = minutesAgo(8 * 60 + 31);
        when(mealRepository.findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user))
            .thenReturn(java.util.List.of(meal(start)));

        FastingPeriod period = service.findActiveAutomaticPeriod(user).orElseThrow();

        assertEquals(minutesAfter(start, 30), period.getStartTime());
        assertEquals(FastingPeriodSource.AUTOMATIC, period.getSource());
        assertEquals(null, period.getEndTime());
    }

    @Test
    void doesNotFindCurrentAutomaticPeriodBeforeEightHours() {
        User user = user(1L);
        OffsetDateTime start = minutesAgo(8 * 60 + 29);
        when(mealRepository.findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user))
            .thenReturn(java.util.List.of(meal(start)));

        assertTrue(service.findActiveAutomaticPeriod(user).isEmpty());
    }

    @Test
    void savesCompletedAutomaticPeriodAfterEightHours() {
        User user = user(1L);
        OffsetDateTime start = minutesAgo(9 * 60);
        OffsetDateTime end = minutesAfter(start, 8 * 60 + 30);
        when(mealRepository.findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user))
            .thenReturn(java.util.List.of(meal(start), meal(end)));

        service.recalculateAutomaticPeriods(user);

        ArgumentCaptor<FastingPeriod> period = ArgumentCaptor.forClass(FastingPeriod.class);
        verify(repository).save(period.capture());
        assertEquals(minutesAfter(start, 30), period.getValue().getStartTime());
        assertEquals(end, period.getValue().getEndTime());
        assertEquals(FastingPeriodSource.AUTOMATIC, period.getValue().getSource());
    }

    @Test
    void calculatesDinnerToBreakfastFromMealEnd() {
        User user = user(1L);
        OffsetDateTime dinner = OffsetDateTime.parse("2026-08-10T20:00:00+02:00");
        OffsetDateTime breakfast = dinner.plusDays(1).withHour(8);
        when(mealRepository.findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user))
            .thenReturn(java.util.List.of(meal(dinner), meal(breakfast)));
        service.recalculateAutomaticPeriods(user);
        ArgumentCaptor<FastingPeriod> periods = ArgumentCaptor.forClass(FastingPeriod.class);
        verify(repository, org.mockito.Mockito.times(2)).save(periods.capture());
        assertEquals(java.time.Duration.ofMinutes(690), java.time.Duration.between(
            periods.getAllValues().getFirst().getStartTime(), periods.getAllValues().getFirst().getEndTime()));
    }

    @Test
    void skipsGapReducedBelowEightHours() {
        User user = user(1L);
        OffsetDateTime start = minutesAgo(9 * 60);
        when(mealRepository.findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user))
            .thenReturn(java.util.List.of(meal(start), meal(minutesAfter(start, 8 * 60 + 29))));
        service.recalculateAutomaticPeriods(user);
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void overlappingAndTouchingMealsExtendEatingUntilLatestEnd() {
        User user = user(1L);
        OffsetDateTime start = minutesAgo(12 * 60);
        Meal dinner = meal(start);
        dinner.setDurationMinutes(120);
        Meal snack = meal(minutesAfter(start, 30));
        Meal touching = meal(minutesAfter(start, 120));
        when(mealRepository.findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user))
            .thenReturn(java.util.List.of(dinner, snack, touching));
        service.recalculateAutomaticPeriods(user);
        ArgumentCaptor<FastingPeriod> period = ArgumentCaptor.forClass(FastingPeriod.class);
        verify(repository).save(period.capture());
        assertEquals(minutesAfter(start, 150), period.getValue().getStartTime());
        assertEquals(period.getValue().getStartTime(), service.findActiveAutomaticPeriod(user).orElseThrow().getStartTime());
    }

    @Test
    void mealDurationUsesElapsedMinutesAcrossMidnightAndClockChanges() {
        User user = user(1L);
        for (String local : java.util.List.of("2026-03-28T23:45", "2026-03-29T01:45", "2025-10-26T02:45")) {
            OffsetDateTime start = java.time.LocalDateTime.parse(local).atZone(DateTimes.USER_ZONE).toOffsetDateTime();
            when(mealRepository.findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user))
                .thenReturn(java.util.List.of(meal(start)));
            assertEquals(start.toInstant().plusSeconds(1800), service.findActiveAutomaticPeriod(user).orElseThrow().getStartTime().toInstant());
        }
    }

    private Meal meal(OffsetDateTime timestamp) {
        Meal meal = new Meal();
        meal.setMealDate(LocalDate.from(timestamp));
        meal.setMealTime(LocalTime.from(timestamp));
        meal.setDurationMinutes(30);
        return meal;
    }

    private OffsetDateTime minutesAgo(int minutes) {
        return ZonedDateTime.now(DateTimes.USER_ZONE).minusMinutes(minutes).toOffsetDateTime();
    }

    private OffsetDateTime minutesAfter(OffsetDateTime start, int minutes) {
        return start.atZoneSameInstant(DateTimes.USER_ZONE).plusMinutes(minutes).toOffsetDateTime();
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
