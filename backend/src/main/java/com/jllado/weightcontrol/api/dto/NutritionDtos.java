package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.service.NutritionService.DailyNutritionSummary;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.domain.MacroTargetComparison;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.Valid;

public final class NutritionDtos {

    public record NutrientCoverage(int foodsWithValues, int estimatedFoods) { }

    // Legacy shared counts cover foods with all three original nutrients; each total uses its own known values.
    public record NutrientSummary(BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams,
        int foodsWithValues, int totalFoods, int estimatedFoods, int mealsWithoutFoods,
        BigDecimal epaDhaMilligrams, NutrientCoverage vitaminDCoverage, NutrientCoverage omega3Coverage,
        NutrientCoverage magnesiumCoverage, NutrientCoverage epaDhaCoverage,
        BigDecimal alaMilligrams, NutrientCoverage alaCoverage,
        BigDecimal fiberGrams, NutrientCoverage fiberCoverage) {
        public NutrientSummary(BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams,
            int foodsWithValues, int totalFoods, int estimatedFoods, int mealsWithoutFoods) {
            this(vitaminDMicrograms, omega3Milligrams, magnesiumMilligrams, foodsWithValues, totalFoods, estimatedFoods, mealsWithoutFoods,
                null, new NutrientCoverage(foodsWithValues, estimatedFoods), new NutrientCoverage(foodsWithValues, estimatedFoods),
                new NutrientCoverage(foodsWithValues, estimatedFoods), new NutrientCoverage(0, 0),
                null, new NutrientCoverage(0, 0), null, new NutrientCoverage(0, 0));
        }
    }

    public enum NutrientTargetSource { PERSONAL, EFSA_AI, NONE }

    public record NutrientTargetValue(BigDecimal value, NutrientTargetSource source, BigDecimal referenceValue) { }

    public record NutrientTargetsResponse(NutrientTargetValue vitaminD, NutrientTargetValue omega3, NutrientTargetValue magnesium,
        NutrientTargetValue epaDha, NutrientTargetValue ala, NutrientTargetValue fiber) {
        public NutrientTargetsResponse(NutrientTargetValue vitaminD, NutrientTargetValue omega3, NutrientTargetValue magnesium,
            NutrientTargetValue epaDha) {
            this(vitaminD, omega3, magnesium, epaDha, null, null);
        }
    }

    public record MacroTargetValue(BigDecimal value, MacroTargetComparison comparison, String unavailableReason) { }

    public record MacroTargetsResponse(MacroTargetValue protein, MacroTargetValue carbohydrate, MacroTargetValue fat,
        int calorieBudget) {
        public MacroTargetsResponse(MacroTargetValue protein, MacroTargetValue carbohydrate, MacroTargetValue fat) {
            this(protein, carbohydrate, fat, 0);
        }
    }

    public record MacroTargetSettings(
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @DecimalMin("0.01") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal fatPercentage) { }

    public record PersonalNutrientTarget(@DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal value) { }

    public record NutrientTargetOverrides(
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal vitaminDMicrograms,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal omega3Milligrams,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal magnesiumMilligrams,
        @Valid MacroTargetSettings macros, @Valid PersonalNutrientTarget epaDha,
        @Valid PersonalNutrientTarget ala, @Valid PersonalNutrientTarget fiber) {
        public NutrientTargetOverrides(BigDecimal vitaminDMicrograms, BigDecimal omega3Milligrams, BigDecimal magnesiumMilligrams) {
            this(vitaminDMicrograms, omega3Milligrams, magnesiumMilligrams, null, null, null, null);
        }
    }

    public record NutrientTargetSettingsResponse(NutrientTargetsResponse targets, NutrientTargetOverrides overrides,
        MacroTargetsResponse macroTargets) {
        public NutrientTargetSettingsResponse(NutrientTargetsResponse targets, NutrientTargetOverrides overrides) {
            this(targets, overrides, null);
        }
    }

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
        boolean completed,
        MacroTargetsResponse macroTargets) {
        public DailyNutritionSummaryResponse(String dateFormat, LocalDate date, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, boolean macrosComplete) {
            this(dateFormat, date, calories, proteinGrams, carbohydrateGrams, fatGrams, macrosComplete, null, false, null);
        }
        public DailyNutritionSummaryResponse(String dateFormat, LocalDate date, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, boolean macrosComplete, NutrientSummary nutrients) {
            this(dateFormat, date, calories, proteinGrams, carbohydrateGrams, fatGrams, macrosComplete, nutrients, false, null);
        }
        public DailyNutritionSummaryResponse(String dateFormat, LocalDate date, int calories, BigDecimal proteinGrams,
            BigDecimal carbohydrateGrams, BigDecimal fatGrams, boolean macrosComplete, NutrientSummary nutrients,
            boolean completed) {
            this(dateFormat, date, calories, proteinGrams, carbohydrateGrams, fatGrams, macrosComplete, nutrients, completed, null);
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
                summary.completed(),
                summary.macroTargets()
            );
        }
    }
}
