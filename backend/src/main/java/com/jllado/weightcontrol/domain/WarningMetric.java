package com.jllado.weightcontrol.domain;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record WarningMetric(
    @NotBlank @Size(max = 80) String key,
    @NotBlank @Size(max = 60) String label,
    @NotBlank @Size(max = 120) String source,
    @NotNull Aggregation aggregation,
    @NotBlank @Size(max = 24) String unit,
    BigDecimal value,
    BigDecimal minimum,
    BigDecimal maximum,
    @Min(0) @Max(90) int coveredDays,
    @NotBlank @Size(max = 500) String goalBasis
) {
    public enum Aggregation { SUM, MEAN, COUNT, LATEST }
}
