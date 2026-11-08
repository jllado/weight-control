package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSource;
import com.jllado.weightcontrol.domain.MacroTargetComparison;
import java.math.BigDecimal;

/** Intake evidence and saved targets, never a diagnosis or an inferred omega-3 allocation. */
public final class NutrientAssessment {
    private NutrientAssessment() { }
    public record Result(String code, String label, String color) { }
    private static final Result MET = new Result("GOAL_MET", "Goal met", "#2f855a");
    private static final Result BELOW = new Result("BELOW_GOAL", "Below goal", "#c05621");
    private static final Result ABOVE = new Result("ABOVE_GOAL", "Above goal", "#c05621");
    private static final Result UNKNOWN = new Result("UNAVAILABLE", "Not available", "#486277");
    public static Result vitaminD(BigDecimal intake, BigDecimal target, NutrientTargetSource source, boolean complete) {
        if (target != null && source == NutrientTargetSource.EFSA_AI)
            return new Result("REFERENCE_ONLY", "Reference only", "#486277");
        if (source != NutrientTargetSource.PERSONAL) return UNKNOWN;
        return compare(intake, target, MacroTargetComparison.MINIMUM, complete);
    }
    public static Result omega3(BigDecimal epaDha, BigDecimal epaDhaTarget, boolean epaDhaComplete,
        BigDecimal ala, BigDecimal alaTarget, boolean alaComplete) {
        var marine = compare(epaDha, epaDhaTarget, MacroTargetComparison.MINIMUM, epaDhaComplete);
        var plant = compare(ala, alaTarget, MacroTargetComparison.MINIMUM, alaComplete);
        if (marine == BELOW || plant == BELOW) return BELOW;
        return marine == MET && plant == MET ? MET : UNKNOWN;
    }
    public static Result compare(BigDecimal intake, BigDecimal target, MacroTargetComparison comparison, boolean complete) {
        if (intake == null || target == null || comparison == null) return UNKNOWN;
        if (comparison == MacroTargetComparison.MINIMUM)
            return intake.compareTo(target) >= 0 ? MET : complete ? BELOW : UNKNOWN;
        return intake.compareTo(target) > 0 ? ABOVE : complete ? MET : UNKNOWN;
    }
}
