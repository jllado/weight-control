package com.jllado.weightcontrol.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record WarningMetricReview(
    @Min(1) @Max(90) int windowDays,
    @NotNull LocalDate windowEnd,
    @NotNull Boolean todayComplete,
    @NotBlank @Size(max = 120) String shortStatus,
    @NotNull @Size(min = 1, max = 12) List<@NotNull @Valid WarningMetric> metrics,
    @Size(max = 500) String definitionChangeReason
) {}
