package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.CoachWarningType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WarningGoalStatus;
import com.jllado.weightcontrol.domain.WarningMetric;
import com.jllado.weightcontrol.domain.WarningMetricReview;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class WarningCreationEligibility {
    private final NutritionService nutrition;

    public WarningCreationEligibility(NutritionService nutrition) { this.nutrition = nutrition; }

    public void validate(User user, CoachWarningType type, WarningMetricReview review) {
        if (type != CoachWarningType.NUTRITION_IMBALANCE || review.windowDays() != 7) return;
        var unmet = review.metrics().stream().filter(metric -> WarningMetricEvaluator.status(metric, 7) == WarningGoalStatus.NOT_MET).toList();
        if (unmet.isEmpty() || unmet.stream().anyMatch(metric -> !calorieMean(metric) || metric.maximum() == null)) return;
        if (review.metrics().stream().anyMatch(metric -> calorieMean(metric) && WarningMetricEvaluator.status(metric, 7) == WarningGoalStatus.UNKNOWN)) return;

        var first = review.windowEnd().minusDays(7);
        var days = nutrition.findBetween(user, first, review.windowEnd());
        // The owner-scoped range contains one aggregate per recorded calendar date: eight rows cover both shifted windows.
        if (days.size() != 8 || days.stream().anyMatch(day -> !day.completed())) return;
        var current = BigDecimal.valueOf(days.stream().filter(day -> !day.date().equals(first)).mapToLong(NutritionService.DailyNutritionSummary::calories).sum());
        var previous = BigDecimal.valueOf(days.stream().filter(day -> !day.date().equals(review.windowEnd())).mapToLong(NutritionService.DailyNutritionSummary::calories).sum());
        boolean isolated = unmet.stream().allMatch(metric -> {
            var goalTotal = metric.maximum().multiply(BigDecimal.valueOf(7));
            return current.compareTo(goalTotal) > 0
                && current.compareTo(goalTotal.multiply(new BigDecimal("1.05"))) <= 0
                && previous.compareTo(goalTotal) <= 0;
        });
        if (isolated) throw new BadRequestException("An isolated calorie average up to 5% above goal cannot create a warning; omit this new warning and reassess the next completed calendar-day window");
    }

    private boolean calorieMean(WarningMetric metric) {
        return metric.aggregation() == WarningMetric.Aggregation.MEAN && metric.unit().equals("kcal/day")
            && (metric.source().equals("NUTRITION.dailyTotals.calories/completed")
                || metric.source().equals("NUTRITION.dailyTotals.calories") || metric.source().equals("NUTRITION.calories"));
    }
}
