package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.service.WeightPerformanceWeek;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;

public final class WeightDtos {

    private WeightDtos() {
    }

    public record WeightRequest(
        @NotNull OffsetDateTime date,
        @NotNull @DecimalMin("0.0") BigDecimal weight,
        @NotNull @DecimalMin("0.0") BigDecimal fatPercentage,
        @NotNull @DecimalMin("0.0") BigDecimal muscle
    ) {
    }

    public record CoachWeightRequest(
        @NotNull OffsetDateTime date,
        @NotNull @DecimalMin("0.0") BigDecimal weight,
        @NotNull @DecimalMin("0.0") BigDecimal fatPercentage,
        @NotNull @DecimalMin("0.0") BigDecimal muscle,
        @AssertTrue boolean confirmed
    ) {
        public WeightRequest weightRequest() {
            return new WeightRequest(date, weight, fatPercentage, muscle);
        }
    }

    public record WeightResponse(
        Long id,
        String dateFormat,
        OffsetDateTime date,
        BigDecimal weight,
        BigDecimal fatPercentage,
        BigDecimal fat,
        BigDecimal muscle,
        BigDecimal musclePercentage,
        BigDecimal lostWeight,
        BigDecimal lostFat,
        BigDecimal lostMuscle,
        PerformanceWeekResponse performanceWeek,
        String photoFront,
        String photoLeft,
        String photoRight
    ) {
        public static WeightResponse from(Weight weight, String photoFront, String photoLeft, String photoRight, WeightPerformanceWeek performanceWeek) {
            return new WeightResponse(
                weight.getId(),
                DateTimes.formatDate(weight.getMeasuredAt()),
                weight.getMeasuredAt(),
                weight.getWeight(),
                weight.getFatPercentage(),
                weight.getFat(),
                weight.getMuscle(),
                weight.getMusclePercentage(),
                weight.getLostWeight(),
                weight.getLostFat(),
                weight.getLostMuscle(),
                PerformanceWeekResponse.from(performanceWeek),
                photoFront,
                photoLeft,
                photoRight
            );
        }
    }

    public record PerformanceWeekResponse(LocalDate startDate, LocalDate endDate, BigDecimal routineCompletionPercentage) {
        public static PerformanceWeekResponse from(WeightPerformanceWeek performanceWeek) {
            return performanceWeek == null ? null : new PerformanceWeekResponse(
                performanceWeek.startDate(), performanceWeek.endDate(), performanceWeek.routineCompletionPercentage()
            );
        }
    }
}
