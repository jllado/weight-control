package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WeeklyReflectionDtos {

    private WeeklyReflectionDtos() {
    }

    public record WeeklyReflectionOverviewResponse(
        LocalDate latestEligibleFriday,
        boolean actionConfigured,
        List<WeeklySummaryReflectionItem> summaries
    ) {
    }

    public record WeeklySummaryReflectionItem(
        LocalDate fridayDate,
        boolean reflectionSaved
    ) {
    }

    public record WeeklyReflectionContextResponse(
        LocalDate periodStart,
        LocalDate fridayDate,
        WeeklySummarySnapshot summary,
        WeeklyReflectionResponse existingReflection
    ) {
        public static WeeklyReflectionContextResponse from(SavedWeeklySummary saved, WeeklySummarySnapshot snapshot, WeeklyReflection reflection) {
            return new WeeklyReflectionContextResponse(
                snapshot.periodStart(),
                saved.getFridayDate(),
                snapshot,
                reflection == null ? null : WeeklyReflectionResponse.from(reflection)
            );
        }
    }

    public record SaveWeeklyReflectionRequest(
        @NotNull Boolean confirmed,
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 500) String summary,
        @NotNull @Valid WeeklyReflectionSection bodyComposition,
        @NotNull @Valid WeeklyReflectionSection bloodPressure,
        @NotNull @Valid WeeklyReflectionSection routines,
        @NotNull @Valid WeeklyReflectionSection nutrition,
        @NotNull @Valid WeeklyReflectionSection trainingRecovery,
        @NotNull @Valid WeeklyReflectionSection goalProgress,
        @NotNull @Size(min = 1, max = 5) List<@NotBlank @Size(max = 120) String> nextWeekActions
    ) {
        @AssertTrue(message = "Weekly reflection save requires exact user confirmation")
        public boolean isConfirmed() {
            return Boolean.TRUE.equals(confirmed);
        }
    }

    public record WeeklyReflectionSection(
        @NotBlank @Size(max = 400) String summary,
        @NotBlank @Size(max = 200) String nextAction
    ) {
    }

    public record WeeklyReflectionResponse(
        LocalDate fridayDate,
        Instant generatedAt,
        String model,
        String title,
        String summary,
        WeeklyReflectionSection bodyComposition,
        WeeklyReflectionSection bloodPressure,
        WeeklyReflectionSection routines,
        WeeklyReflectionSection nutrition,
        WeeklyReflectionSection trainingRecovery,
        WeeklyReflectionSection goalProgress,
        List<String> nextWeekActions
    ) {
        public static WeeklyReflectionResponse from(WeeklyReflection reflection) {
            return new WeeklyReflectionResponse(
                reflection.getWeeklySummary().getFridayDate(),
                reflection.getGeneratedAt(),
                reflection.getModel(),
                reflection.getTitle(),
                reflection.getSummary(),
                section(reflection.getBodyCompositionSummary(), reflection.getBodyCompositionNextAction()),
                section(reflection.getBloodPressureSummary(), reflection.getBloodPressureNextAction()),
                section(reflection.getRoutinesSummary(), reflection.getRoutinesNextAction()),
                section(reflection.getNutritionSummary(), reflection.getNutritionNextAction()),
                section(reflection.getTrainingRecoverySummary(), reflection.getTrainingRecoveryNextAction()),
                section(reflection.getGoalProgressSummary(), reflection.getGoalProgressNextAction()),
                reflection.getNextWeekActions()
            );
        }
    }

    private static WeeklyReflectionSection section(String summary, String nextAction) {
        return new WeeklyReflectionSection(summary, nextAction);
    }
}
