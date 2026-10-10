package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.service.NutritionService.DailyNutritionSummary;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public final class NutritionDtos {

    public record NutrientSummary(BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams,
        int foodsWithValues, int totalFoods, int estimatedFoods, int mealsWithoutFoods) { }

    public enum NutrientTargetSource { PERSONAL, EFSA_AI, NONE }

    public record NutrientTargetValue(BigDecimal value, NutrientTargetSource source, BigDecimal referenceValue) { }

    public record NutrientTargetsResponse(NutrientTargetValue vitaminD, NutrientTargetValue omega3, NutrientTargetValue magnesium) { }

    public record NutrientTargetOverrides(
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal vitaminDMicrograms,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal omega3Milligrams,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal magnesiumMilligrams) { }

    public record NutrientTargetSettingsResponse(NutrientTargetsResponse targets, NutrientTargetOverrides overrides) { }

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
        NutrientSummary nutrients,
        boolean completed) {
        public DailyNutritionSummaryResponse(String dateFormat, LocalDate date, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, boolean macrosComplete) {
            this(dateFormat, date, calories, proteinGrams, carbohydrateGrams, fatGrams, macrosComplete, null, false);
        }
        public DailyNutritionSummaryResponse(String dateFormat, LocalDate date, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, boolean macrosComplete, NutrientSummary nutrients) {
            this(dateFormat, date, calories, proteinGrams, carbohydrateGrams, fatGrams, macrosComplete, nutrients, false);
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
                summary.nutrients(),
                summary.completed()
            );
        }
    }
}
