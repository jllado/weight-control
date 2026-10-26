package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class WarningMetricFixtures {
    private WarningMetricFixtures() {}
    public static WarningMetricEvaluator evaluator() {
        return new WarningMetricEvaluator(jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());
    }
    public static WarningMetricReview review(LocalDate date, boolean met) {
        return review(date, met ? WarningDirection.RESOLVED : WarningDirection.UNCHANGED);
    }
    public static WarningMetricReview review(LocalDate date, WarningDirection direction) {
        boolean met = direction == WarningDirection.RESOLVED;
        int covered = direction == WarningDirection.INSUFFICIENT_EVIDENCE ? 6 : 7;
        return new WarningMetricReview(7, date, true, met ? "Both sleep goals met." : "Sleep goals need more recorded evidence.",
            List.of(new WarningMetric("asleep", "Asleep", "RECOVERY.totalSleepSeconds", WarningMetric.Aggregation.SUM, "seconds",
                    BigDecimal.valueOf(met ? 168000 : 140000), BigDecimal.valueOf(151200), null, covered, "Personal 6-hour daily minimum across 7 days"),
                new WarningMetric("inBed", "In bed", "RECOVERY.timeInBedSeconds", WarningMetric.Aggregation.SUM, "seconds",
                    BigDecimal.valueOf(met ? 200000 : 170000), BigDecimal.valueOf(176400), null, covered, "Personal 7-hour daily minimum across 7 days")), null);
    }
}
