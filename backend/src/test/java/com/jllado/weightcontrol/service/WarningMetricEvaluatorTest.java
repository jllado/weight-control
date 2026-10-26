package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarningMetricEvaluatorTest {
    private final LocalDate today = LocalDate.of(2026, 10, 26);
    private final WarningMetricEvaluator evaluator = WarningMetricFixtures.evaluator();

    @Test void completeSleepTotalsMeetGoalsWithoutCountingPassingNights() {
        var review = WarningMetricFixtures.review(today, true);
        assertEquals(WarningGoalStatus.MET, evaluator.evaluate(review, today, null));
        assertEquals(WarningGoalStatus.NOT_MET, evaluator.evaluate(WarningMetricFixtures.review(today, false), today, null));
    }

    @Test void inclusiveMinimumAndMaximumUseUnroundedValues() {
        assertEquals(WarningGoalStatus.MET, evaluateCalories("2500", 7));
        assertEquals(WarningGoalStatus.MET, evaluateCalories("2499.99", 7));
        assertEquals(WarningGoalStatus.NOT_MET, evaluateCalories("2500.01", 7));
        assertEquals(WarningGoalStatus.UNKNOWN, evaluateCalories("2500", 6));
        var metric = new WarningMetric("sleep", "Asleep", "RECOVERY.totalSleepSeconds", WarningMetric.Aggregation.SUM, "seconds",
            new BigDecimal("151200"), new BigDecimal("151200"), null, 7, "Personal goal");
        assertEquals(WarningGoalStatus.MET, WarningMetricEvaluator.status(metric, 7));
    }

    @Test void missingValuesOrGoalsCannotResolve() {
        var value = new WarningMetric("pain", "Pain", "RECOVERY.backPain", WarningMetric.Aggregation.MEAN, "rating",
            null, null, BigDecimal.ONE, 7, "Agreed personal goal");
        assertEquals(WarningGoalStatus.UNKNOWN, WarningMetricEvaluator.status(value, 7));
        var goal = new WarningMetric("pain", "Pain", "RECOVERY.backPain", WarningMetric.Aggregation.MEAN, "rating",
            BigDecimal.ZERO, null, null, 7, "No agreed goal yet");
        assertEquals(WarningGoalStatus.UNKNOWN, WarningMetricEvaluator.status(goal, 7));
    }

    @Test void rollingWindowUsesTodayWhenCompleteOtherwiseYesterday() {
        var review = WarningMetricFixtures.review(today, true);
        assertEquals(WarningGoalStatus.MET, evaluator.evaluate(review, today, null));
        var yesterday = new WarningMetricReview(7, today.minusDays(1), false, review.shortStatus(), review.metrics(), null);
        assertEquals(WarningGoalStatus.MET, evaluator.evaluate(yesterday, today, null));
        assertThrows(BadRequestException.class, () -> evaluator.evaluate(yesterday, today.plusDays(1), null));
        assertThrows(BadRequestException.class, () -> evaluator.evaluate(review, today.minusDays(1), null));
    }

    @Test void changingWindowRequiresReasonAndUnknownRowsPreventOverallResolution() {
        var before = WarningMetricFixtures.review(today, true);
        var after = new WarningMetricReview(8, today, true, before.shortStatus(), before.metrics(), null);
        assertThrows(BadRequestException.class, () -> evaluator.evaluate(after, today, before));
        var explained = new WarningMetricReview(8, today, true, before.shortStatus(), before.metrics(), "User changed the assessment window");
        assertEquals(WarningGoalStatus.UNKNOWN, evaluator.evaluate(explained, today, before));
    }

    private WarningGoalStatus evaluateCalories(String value, int covered) {
        var metric = new WarningMetric("calories", "Calories", "NUTRITION.calories", WarningMetric.Aggregation.MEAN, "kcal/day",
            new BigDecimal(value), null, new BigDecimal("2500"), covered, "Saved personal maximum");
        return evaluator.evaluate(new WarningMetricReview(7, today, true, "Review calorie intake.", List.of(metric), null), today, null);
    }
}
