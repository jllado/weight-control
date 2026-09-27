package com.jllado.weightcontrol.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class OverallProgressDtos {
    private OverallProgressDtos() {}

    public record OverallProgressResponse(String status, BigDecimal score, LocalDate currentStart, LocalDate currentEnd,
                                          LocalDate previousStart, LocalDate previousEnd,
                                          List<MetricContribution> contributions) {}

    public record MetricContribution(String metric, boolean included, BigDecimal weight, BigDecimal change,
                                     BigDecimal normalizedContribution, String explanation) {}
}
