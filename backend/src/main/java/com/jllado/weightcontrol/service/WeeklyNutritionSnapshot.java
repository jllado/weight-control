package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.NutritionDtos.*;
import com.jllado.weightcontrol.domain.MacroTargetComparison;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WeeklyNutritionSnapshot(Instant capturedAt, String targetLabel, NutrientTargetsResponse targets,
    Period current, Period previous, Period yearAgo) {
    public record Period(LocalDate startDate, LocalDate endDate, int completedDays, int recordedDays,
        List<NutritionService.DailyNutritionSummary> days, List<DatedTarget> datedTargets, List<Measure> measures) { }
    public record DatedTarget(LocalDate date, MacroTargetsResponse macroTargets, BigDecimal fatPercentage) { }
    public record Measure(String key, String label, String unit, BigDecimal average, int eligibleDays,
        int recordedDays, int coveredFoods, int totalFoods, int estimatedFoods, BigDecimal target,
        MacroTargetComparison comparison, String unavailableReason) { }
}
