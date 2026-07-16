package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public final class DecisionOutcomeDtos {

    private DecisionOutcomeDtos() {
    }

    public record DecisionOutcomeRequest(
        @NotNull LocalDate date,
        @NotNull DecisionOutcomeType outcome
    ) {
    }

    public record DecisionOutcomeResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        DecisionOutcomeType outcome
    ) {
        public static DecisionOutcomeResponse from(DecisionOutcome decisionOutcome) {
            return new DecisionOutcomeResponse(
                decisionOutcome.getId(),
                DateTimes.formatDate(decisionOutcome.getOutcomeDate()),
                decisionOutcome.getOutcomeDate(),
                decisionOutcome.getOutcome()
            );
        }
    }
}
