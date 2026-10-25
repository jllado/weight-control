package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.CoachWarningType;
import com.jllado.weightcontrol.domain.WarningDirection;
import com.jllado.weightcontrol.domain.WarningReviewSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReflectionDtos {

    private ReflectionDtos() {
    }

    public record ReflectionValidationResponse(String message, List<ReflectionValidationError> errors) {
    }

    public record ReflectionValidationError(String field, String message) {
    }

    public record ReflectionOverviewResponse(
        LocalDate firstTrackedDate,
        LocalDate lastCompletedDate,
        boolean actionConfigured,
        List<ReflectionSummaryResponse> reflections
    ) {
    }

    public record PagedReflectionOverviewResponse(
        LocalDate firstTrackedDate,
        LocalDate lastCompletedDate,
        boolean actionConfigured,
        List<ReflectionSummaryResponse> reflections,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record SaveReflectionRequest(
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 200) String summary,
        @Min(1) @Max(10) Integer planProgressScore,
        @Size(max = 120) String planProgressRationale,
        @NotNull @Size(min = 1, max = 1) List<@NotBlank @Size(max = 120) String> positiveSignals,
        @NotNull @Size(min = 1, max = 1) List<@NotBlank @Size(max = 120) String> watchouts,
        @NotNull @Size(min = 1, max = 1) List<@NotBlank @Size(max = 200) String> nextActions,
        @NotNull @Valid ReflectionSection meals,
        @NotNull @Valid ReflectionSection workouts,
        @NotNull @Valid WarningReviewEnvelope warningReview
    ) {
        @AssertTrue(message = "Plan progress score and rationale must be provided together")
        public boolean hasCompletePlanProgressRating() {
            return planProgressScore == null && planProgressRationale == null
                || planProgressScore != null && planProgressRationale != null && !planProgressRationale.isBlank();
        }
    }

    public record WarningReviewEnvelope(
        @NotNull LocalDate reviewedDate,
        @NotNull List<@NotNull @Valid ExistingWarningReview> reviews,
        @NotNull List<@NotNull @Valid NewWarningReview> newWarnings
    ) {}

    public record ExistingWarningReview(
        @NotNull @Positive Long id,
        @NotNull @PositiveOrZero Long version,
        @NotNull @Valid WarningAssessment assessment
    ) {}

    public record NewWarningReview(
        @NotNull UUID requestKey,
        @NotNull CoachWarningType type,
        LocalDate onsetDate,
        @NotNull @Valid WarningAssessment assessment
    ) {}

    public record WarningAssessment(
        @NotNull WarningDirection direction,
        @NotBlank @Size(max = 500) String assessment,
        @NotBlank @Size(max = 2000) String evidence,
        @NotBlank @Size(max = 500) String nextAction,
        @NotBlank @Size(max = 500) String resolutionCriteria,
        @NotBlank @Size(max = 500) String criteriaProgress,
        @NotNull LocalDate recentStart,
        @NotNull LocalDate recentEnd,
        LocalDate priorStart,
        LocalDate priorEnd,
        @Size(max = 500) String criteriaChangeReason,
        @Size(max = 2000) String resolutionRationale
    ) {}

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
        ReflectionSection workouts,
        WarningReviewSnapshot warningReview
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
                ReflectionSection.from(reflection.getWorkoutsSummary(), reflection.getWorkoutsNextAction()),
                reflection.getWarningReview()
            );
        }
    }
}
