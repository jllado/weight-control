package com.jllado.weightcontrol.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;

public final class WeeklyCaloriePlanDtos {
    private WeeklyCaloriePlanDtos() { }

    public record DayPlanRequest(@NotNull LocalDate date,
        @NotNull @DecimalMin("0") @DecimalMax("2147483647") @Digits(integer = 10, fraction = 0) BigDecimal calories) {
        public DayPlanRequest(LocalDate date, int calories) { this(date, BigDecimal.valueOf(calories)); }
    }

    public record WeeklyCaloriePlanRequest(
        @NotNull LocalDate weekStart,
        Long revision,
        @NotNull @Size(min = 1, max = 7) List<@NotNull @Valid DayPlanRequest> days
    ) { }

    public record DayPlanResponse(LocalDate date, int calories) { }

    public record WeeklyCaloriePlanResponse(LocalDate weekStart, Long revision, List<DayPlanResponse> days) { }
}
