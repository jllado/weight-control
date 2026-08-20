package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.CoachingPlan;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CoachingPlanDtos {

    private CoachingPlanDtos() {
    }

    public record CoachingPlanRequest(
        @NotBlank @Size(max = 255) String goal,
        @NotNull List<@NotBlank String> principles,
        @NotNull List<@NotBlank String> priorities,
        @NotNull List<@NotBlank String> actions,
        @NotNull LocalDate startDate,
        LocalDate reviewDate,
        String notes
    ) {
    }

    public record CoachCoachingPlanRequest(
        @NotBlank @Size(max = 255) String goal,
        @NotNull List<@NotBlank String> principles,
        @NotNull List<@NotBlank String> priorities,
        @NotNull List<@NotBlank String> actions,
        @NotNull LocalDate startDate,
        LocalDate reviewDate,
        String notes,
        @AssertTrue boolean confirmed
    ) {
        public CoachingPlanRequest plan() {
            return new CoachingPlanRequest(goal, principles, priorities, actions, startDate, reviewDate, notes);
        }
    }

    public record CoachingPlanResponse(
        String goal,
        List<String> principles,
        List<String> priorities,
        List<String> actions,
        LocalDate startDate,
        LocalDate reviewDate,
        String notes,
        Instant updatedAt
    ) {
        public static CoachingPlanResponse from(CoachingPlan plan) {
            return new CoachingPlanResponse(
                plan.getGoal(),
                plan.getPrinciples(),
                plan.getPriorities(),
                plan.getActions(),
                plan.getStartDate(),
                plan.getReviewDate(),
                plan.getNotes(),
                plan.getUpdatedAt()
            );
        }
    }
}
