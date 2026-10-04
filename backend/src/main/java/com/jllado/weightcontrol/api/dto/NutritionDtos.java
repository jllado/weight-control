package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.service.NutritionService.DailyNutritionSummary;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class NutritionDtos {

    public record NutrientSummary(BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams,
        int foodsWithValues, int totalFoods, int estimatedFoods, int mealsWithoutFoods) { }

    private NutritionDtos() {
    }

    public record DailyNutritionSummaryResponse(
        String dateFormat,
        LocalDate date,
        int calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        boolean macrosComplete,
        NutrientSummary nutrients) {
        public DailyNutritionSummaryResponse(String dateFormat, LocalDate date, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, boolean macrosComplete) {
            this(dateFormat, date, calories, proteinGrams, carbohydrateGrams, fatGrams, macrosComplete, null);
        }

        public static DailyNutritionSummaryResponse from(DailyNutritionSummary summary) {
            return new DailyNutritionSummaryResponse(
                DateTimes.formatDate(summary.date()),
                summary.date(),
                summary.calories(),
                summary.proteinGrams(),
                summary.carbohydrateGrams(),
                summary.fatGrams(),
                summary.macrosComplete(),
                summary.nutrients()
            );
        }
    }
}
