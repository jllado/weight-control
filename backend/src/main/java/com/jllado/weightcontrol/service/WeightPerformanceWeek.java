package com.jllado.weightcontrol.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightPerformanceWeek(LocalDate startDate, LocalDate endDate, BigDecimal routineCompletionPercentage) {
}
