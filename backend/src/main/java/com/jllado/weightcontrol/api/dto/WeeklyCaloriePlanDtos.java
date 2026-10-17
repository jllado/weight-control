package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.WeeklyCaloriePlan;
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
        @NotNull @Size(min = 7, max = 7) List<@Valid DayPlanRequest> days
    ) { }

    public record DayPlanResponse(LocalDate date, int calories) { }

    public record WeeklyCaloriePlanResponse(LocalDate weekStart, Long revision, List<DayPlanResponse> days) {
        public static WeeklyCaloriePlanResponse from(WeeklyCaloriePlan plan) {
            return new WeeklyCaloriePlanResponse(plan.getWeekStart(), plan.getRevision(), java.util.stream.IntStream.range(0, 7)
                .mapToObj(index -> new DayPlanResponse(plan.getWeekStart().plusDays(index), plan.getCalories().get(index))).toList());
        }
    }
}
