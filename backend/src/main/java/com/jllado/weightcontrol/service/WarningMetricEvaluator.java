package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.*;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class WarningMetricEvaluator {
    private final Validator validator;

    public WarningMetricEvaluator(Validator validator) { this.validator = validator; }

    public WarningGoalStatus evaluate(WarningMetricReview review, LocalDate reviewedDate, WarningMetricReview previous) {
        if (review == null) throw new BadRequestException("Rolling warning metrics are required; refresh the Coach Action schema");
        var violations = validator.validate(review);
        if (!violations.isEmpty()) throw new BadRequestException(violations.iterator().next().getPropertyPath() + " " + violations.iterator().next().getMessage());
        var expectedEnd = review.todayComplete() ? reviewedDate : reviewedDate.minusDays(1);
        if (!review.windowEnd().equals(expectedEnd)) throw new BadRequestException("Warning window must end today when complete, otherwise yesterday");
        var keys = new HashSet<String>();
        for (var metric : review.metrics()) {
            if (!keys.add(metric.key())) throw new BadRequestException("Warning metric keys must be unique");
            if (metric.coveredDays() > review.windowDays()) throw new BadRequestException("Metric coverage cannot exceed its rolling window");
            if (review.todayComplete() && metric.coveredDays() == 0) throw new BadRequestException("Today's complete records require at least one covered day");
            if (metric.minimum() != null && metric.maximum() != null && metric.minimum().compareTo(metric.maximum()) > 0)
                throw new BadRequestException("Metric minimum cannot exceed maximum");
        }
        if (previous != null && changed(previous, review)
            && (review.definitionChangeReason() == null || review.definitionChangeReason().isBlank()))
            throw new BadRequestException("Explain changes to warning metrics, goals or rolling window");
        if (review.metrics().stream().anyMatch(metric -> status(metric, review.windowDays()) == WarningGoalStatus.UNKNOWN))
            return WarningGoalStatus.UNKNOWN;
        return review.metrics().stream().allMatch(metric -> status(metric, review.windowDays()) == WarningGoalStatus.MET)
            ? WarningGoalStatus.MET : WarningGoalStatus.NOT_MET;
    }

    public static WarningGoalStatus status(WarningMetric metric, int days) {
        if (metric.value() == null || metric.coveredDays() != days || metric.minimum() == null && metric.maximum() == null)
            return WarningGoalStatus.UNKNOWN;
        boolean met = (metric.minimum() == null || metric.value().compareTo(metric.minimum()) >= 0)
            && (metric.maximum() == null || metric.value().compareTo(metric.maximum()) <= 0);
        return met ? WarningGoalStatus.MET : WarningGoalStatus.NOT_MET;
    }

    private boolean changed(WarningMetricReview before, WarningMetricReview after) {
        return before.windowDays() != after.windowDays() || !definitions(before).equals(definitions(after));
    }

    private record Definition(String source, WarningMetric.Aggregation aggregation, String unit,
                              java.math.BigDecimal minimum, java.math.BigDecimal maximum, String goalBasis) {}

    private Map<String, Definition> definitions(WarningMetricReview review) {
        return review.metrics().stream().collect(Collectors.toMap(WarningMetric::key, metric ->
            new Definition(metric.source(), metric.aggregation(), metric.unit(),
                metric.minimum() == null ? null : metric.minimum().stripTrailingZeros(),
                metric.maximum() == null ? null : metric.maximum().stripTrailingZeros(), metric.goalBasis())));
    }
}
