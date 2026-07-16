package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.Numbers;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DecisionOutcomeService {

    private final DecisionOutcomeRepository repository;

    public DecisionOutcomeService(DecisionOutcomeRepository repository) {
        this.repository = repository;
    }

    public DecisionOutcome create(User user, DecisionOutcomeRequest request) {
        if (request.date().isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Decision outcome date cannot be in the future");
        }

        DecisionOutcome decisionOutcome = new DecisionOutcome();
        decisionOutcome.setUser(user);
        decisionOutcome.setOutcomeDate(request.date());
        decisionOutcome.setOutcome(request.outcome());
        return repository.save(decisionOutcome);
    }

    public Summary summarize(User user, LocalDate date) {
        List<DecisionOutcome> outcomes = repository.findByUserAndOutcomeDateLessThanEqualOrderByOutcomeDateAscIdAsc(user, date);
        LocalDate rollingStart = date.minusDays(29);
        LocalDate previousStart = date.minusDays(59);
        LocalDate previousEnd = date.minusDays(30);
        Metrics rolling = metrics(outcomes.stream().filter(outcome -> !outcome.getOutcomeDate().isBefore(rollingStart)).toList());
        Metrics previous = metrics(outcomes.stream()
            .filter(outcome -> !outcome.getOutcomeDate().isBefore(previousStart) && !outcome.getOutcomeDate().isAfter(previousEnd))
            .toList());
        BigDecimal winRateChange = rolling.winRate() == null || previous.winRate() == null
            ? null
            : Numbers.round(rolling.winRate().subtract(previous.winRate()));
        return new Summary(
            metrics(outcomes.stream().filter(outcome -> outcome.getOutcomeDate().equals(date)).toList()),
            rolling,
            previous,
            metrics(outcomes),
            winRateChange,
            currentWinStreak(outcomes)
        );
    }

    private Metrics metrics(List<DecisionOutcome> outcomes) {
        long wins = outcomes.stream().filter(outcome -> outcome.getOutcome() == DecisionOutcomeType.WIN).count();
        long misses = outcomes.size() - wins;
        BigDecimal winRate = outcomes.isEmpty() ? null : Numbers.percentage(wins, outcomes.size());
        return new Metrics(wins, misses, winRate);
    }

    private int currentWinStreak(List<DecisionOutcome> outcomes) {
        int streak = 0;
        for (int index = outcomes.size() - 1; index >= 0 && outcomes.get(index).getOutcome() == DecisionOutcomeType.WIN; index--) {
            streak++;
        }
        return streak;
    }

    public record Metrics(long wins, long misses, BigDecimal winRate) {
    }

    public record Summary(
        Metrics selectedDate,
        Metrics rolling30Days,
        Metrics previous30Days,
        Metrics allTime,
        BigDecimal winRateChange,
        int currentWinStreak
    ) {
    }
}
