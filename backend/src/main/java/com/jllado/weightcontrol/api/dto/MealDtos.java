package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public final class MealDtos {

    private MealDtos() {
    }

    public record MealRequest(
        @NotNull LocalDate date,
        @NotNull MealType mealType,
        @NotNull @DecimalMin("0") Integer calories,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fatGrams,
        LocalTime mealTime,
        String notes
    ) {
    }

    public record CoachMealRequest(
        @NotNull LocalDate date,
        @NotNull MealType mealType,
        @NotNull @DecimalMin("0") Integer calories,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fatGrams,
        LocalTime mealTime,
        String notes,
        @NotNull MealSource source,
        @AssertTrue boolean confirmed
    ) {
        public MealRequest meal() {
            return new MealRequest(
                date,
                mealType,
                calories,
                proteinGrams,
                carbohydrateGrams,
                fatGrams,
                mealTime,
                notes
            );
        }
    }

    public record MealResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        MealType mealType,
        Integer mealSequence,
        LocalTime mealTime,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        String notes,
        MealSource source
    ) {
        public static MealResponse from(Meal meal) {
            return new MealResponse(
                meal.getId(),
                DateTimes.formatDate(meal.getMealDate()),
                meal.getMealDate(),
                meal.getMealType(),
                meal.getMealSequence(),
                meal.getMealTime(),
                meal.getCalories(),
                meal.getProteinGrams(),
                meal.getCarbohydrateGrams(),
                meal.getFatGrams(),
                meal.getNotes(),
                meal.getSource()
            );
        }
    }
}
