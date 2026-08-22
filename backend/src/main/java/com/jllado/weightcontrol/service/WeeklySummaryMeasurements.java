package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.Weight;

public record WeeklySummaryMeasurements(
    PeriodMeasurements currentPeriod,
    PeriodMeasurements previousComparablePeriod,
    PeriodMeasurements yearAgoComparablePeriod
) {

    public record PeriodMeasurements(Weight weight, BloodPressure bloodPressure) {
    }
}
