package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecisionOutcomeServiceTest {

    @Mock
    private DecisionOutcomeRepository repository;

    @InjectMocks
    private DecisionOutcomeService service;

    @Test
    void createStoresDecisionOutcome() {
        User user = user();
        DecisionOutcomeRequest request = new DecisionOutcomeRequest(LocalDate.now(DateTimes.USER_ZONE), DecisionOutcomeType.WIN);

        service.create(user, request);

        ArgumentCaptor<DecisionOutcome> captor = ArgumentCaptor.forClass(DecisionOutcome.class);
        verify(repository).save(captor.capture());
        assertEquals(user, captor.getValue().getUser());
        assertEquals(request.date(), captor.getValue().getOutcomeDate());
        assertEquals(request.outcome(), captor.getValue().getOutcome());
    }

    @Test
    void createAllowsMultipleOutcomesOnTheSameDate() {
        User user = user();
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);

        service.create(user, new DecisionOutcomeRequest(date, DecisionOutcomeType.WIN));
        service.create(user, new DecisionOutcomeRequest(date, DecisionOutcomeType.MISS));

        verify(repository, times(2)).save(any(DecisionOutcome.class));
    }

    @Test
    void createRejectsFutureDate() {
        DecisionOutcomeRequest request = new DecisionOutcomeRequest(LocalDate.now(DateTimes.USER_ZONE).plusDays(1), DecisionOutcomeType.WIN);

        assertThrows(BadRequestException.class, () -> service.create(user(), request));
        verifyNoInteractions(repository);
    }

    @Test
    void summarizeCalculatesDateWindowsTotalsAndStreak() {
        User user = user();
        LocalDate date = LocalDate.of(2026, 7, 16);
        List<DecisionOutcome> outcomes = List.of(
            outcome(1L, date.minusDays(70), DecisionOutcomeType.WIN),
            outcome(2L, date.minusDays(59), DecisionOutcomeType.WIN),
            outcome(3L, date.minusDays(45), DecisionOutcomeType.MISS),
            outcome(4L, date.minusDays(30), DecisionOutcomeType.MISS),
            outcome(5L, date.minusDays(29), DecisionOutcomeType.MISS),
            outcome(6L, date.minusDays(10), DecisionOutcomeType.WIN),
            outcome(7L, date, DecisionOutcomeType.MISS),
            outcome(8L, date, DecisionOutcomeType.WIN),
            outcome(9L, date, DecisionOutcomeType.WIN)
        );
        when(repository.findByUserAndOutcomeDateLessThanEqualOrderByOutcomeDateAscIdAsc(user, date)).thenReturn(outcomes);

        DecisionOutcomeService.Summary summary = service.summarize(user, date);

        assertMetrics(summary.selectedDate(), 2, 1, "66.67");
        assertMetrics(summary.rolling30Days(), 3, 2, "60.00");
        assertMetrics(summary.previous30Days(), 1, 2, "33.33");
        assertMetrics(summary.allTime(), 5, 4, "55.56");
        assertEquals(0, new BigDecimal("26.67").compareTo(summary.winRateChange()));
        assertEquals(2, summary.currentWinStreak());
    }

    @Test
    void summarizeReturnsEmptyMetricsWhenThereAreNoOutcomes() {
        User user = user();
        LocalDate date = LocalDate.of(2026, 7, 16);
        when(repository.findByUserAndOutcomeDateLessThanEqualOrderByOutcomeDateAscIdAsc(user, date)).thenReturn(List.of());

        DecisionOutcomeService.Summary summary = service.summarize(user, date);

        assertEquals(0, summary.selectedDate().wins());
        assertEquals(0, summary.selectedDate().misses());
        assertNull(summary.selectedDate().winRate());
        assertNull(summary.winRateChange());
        assertEquals(0, summary.currentWinStreak());
    }

    @Test
    void summarizeResetsStreakWhenLatestOutcomeIsAMiss() {
        User user = user();
        LocalDate date = LocalDate.of(2026, 7, 16);
        when(repository.findByUserAndOutcomeDateLessThanEqualOrderByOutcomeDateAscIdAsc(user, date)).thenReturn(List.of(
            outcome(1L, date.minusDays(1), DecisionOutcomeType.WIN),
            outcome(2L, date, DecisionOutcomeType.MISS)
        ));

        assertEquals(0, service.summarize(user, date).currentWinStreak());
    }

    private void assertMetrics(DecisionOutcomeService.Metrics metrics, long wins, long misses, String winRate) {
        assertEquals(wins, metrics.wins());
        assertEquals(misses, metrics.misses());
        assertEquals(0, new BigDecimal(winRate).compareTo(metrics.winRate()));
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    private DecisionOutcome outcome(Long id, LocalDate date, DecisionOutcomeType type) {
        DecisionOutcome outcome = new DecisionOutcome();
        outcome.setId(id);
        outcome.setOutcomeDate(date);
        outcome.setOutcome(type);
        return outcome;
    }
}
