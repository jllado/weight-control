package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.DashboardReflection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ReflectionDtos {

    private ReflectionDtos() {
    }

    public record ReflectionOverviewResponse(
        LocalDate firstTrackedDate,
        LocalDate lastCompletedDate,
        boolean actionConfigured,
        List<ReflectionSummaryResponse> reflections
    ) {
    }

    public record SaveReflectionRequest(
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 200) String summary,
        @Min(1) @Max(10) Integer planProgressScore,
        @Size(max = 120) String planProgressRationale,
        @NotNull @Size(min = 1, max = 1) List<@NotBlank @Size(max = 120) String> positiveSignals,
        @NotNull @Size(min = 1, max = 1) List<@NotBlank @Size(max = 120) String> watchouts,
        @NotNull @Size(min = 1, max = 1) List<@NotBlank @Size(max = 120) String> nextActions,
        @Valid ReflectionSection meals,
        @Valid ReflectionSection workouts
    ) {
        @AssertTrue(message = "Plan progress score and rationale must be provided together")
        public boolean hasCompletePlanProgressRating() {
            return planProgressScore == null && planProgressRationale == null
                || planProgressScore != null && planProgressRationale != null && !planProgressRationale.isBlank();
        }
    }

    public record ReflectionSection(
        @NotBlank @Size(max = 200) String summary,
        @NotBlank @Size(max = 120) String nextAction
    ) {
        public static ReflectionSection from(String summary, String nextAction) {
            return summary == null ? null : new ReflectionSection(summary, nextAction);
        }
    }

    public record ReflectionSummaryResponse(
        LocalDate reflectionDate,
        Instant generatedAt,
        String title,
        Integer planProgressScore
    ) {
        public static ReflectionSummaryResponse from(DashboardReflection reflection) {
            return new ReflectionSummaryResponse(
                reflection.getReflectionDate(),
                reflection.getGeneratedAt(),
                reflection.getTitle(),
                reflection.getPlanProgressScore()
            );
        }
    }

    public record ReflectionResponse(
        LocalDate reflectionDate,
        LocalDate windowStart,
        LocalDate detailedWindowStart,
        LocalDate windowEnd,
        Instant generatedAt,
        String model,
        String title,
        String summary,
        Integer planProgressScore,
        String planProgressRationale,
        List<String> positiveSignals,
        List<String> watchouts,
        List<String> nextActions,
        ReflectionSection meals,
        ReflectionSection workouts
    ) {
        public static ReflectionResponse from(DashboardReflection reflection) {
            return new ReflectionResponse(
                reflection.getReflectionDate(),
                reflection.getWindowStart(),
                reflection.getWindowEnd().minusDays(29),
                reflection.getWindowEnd(),
                reflection.getGeneratedAt(),
                reflection.getModel(),
                reflection.getTitle(),
                reflection.getSummary(),
                reflection.getPlanProgressScore(),
                reflection.getPlanProgressRationale(),
                reflection.getPositiveSignals(),
                reflection.getWatchouts(),
                reflection.getNextActions(),
                ReflectionSection.from(reflection.getMealsSummary(), reflection.getMealsNextAction()),
                ReflectionSection.from(reflection.getWorkoutsSummary(), reflection.getWorkoutsNextAction())
            );
        }
    }
}
