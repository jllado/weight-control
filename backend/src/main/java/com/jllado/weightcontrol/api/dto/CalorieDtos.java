package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Calorie;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public final class CalorieDtos {

    private CalorieDtos() {
    }

    public record CalorieRequest(
        @NotNull LocalDate date,
        @NotNull @DecimalMin("0") Integer calories
    ) {
    }

    public record CalorieResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        Integer calories
    ) {
        public static CalorieResponse from(Calorie calorie) {
            return new CalorieResponse(
                calorie.getId(),
                DateTimes.formatDate(calorie.getCalorieDate()),
                calorie.getCalorieDate(),
                calorie.getCalories()
            );
        }
    }
}
