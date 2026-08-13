package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.service.CalorieService.DailyCalories;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;

public final class CalorieDtos {

    private CalorieDtos() {
    }

    public record CalorieResponse(
        String dateFormat,
        LocalDate date,
        Integer calories
    ) {
        public static CalorieResponse from(DailyCalories calories) {
            return new CalorieResponse(DateTimes.formatDate(calories.date()), calories.date(), calories.calories());
        }
    }
}
