package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
        String notes,
        List<@Valid MealDishRequest> dishes
    ) {
        public MealRequest {
            dishes = dishes == null ? List.of() : dishes;
        }
        public MealRequest(LocalDate date, MealType mealType, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, LocalTime mealTime, String notes) {
            this(date, mealType, calories, proteinGrams, carbohydrateGrams, fatGrams, mealTime, notes, List.of());
        }
    }

    public record MealDishRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @DecimalMin("0") Integer calories,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fatGrams
    ) {
    }

    public record CoachMealRequest(
        @NotNull LocalDate date,
        @NotNull MealType mealType,
        @NotNull @DecimalMin("0") Integer calories,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal proteinGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal carbohydrateGrams,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fatGrams,
        @NotNull LocalTime mealTime,
        String notes,
        @NotNull MealSource source,
        @AssertTrue boolean confirmed,
        List<@Valid MealDishRequest> dishes
    ) {
        public CoachMealRequest {
            dishes = dishes == null ? List.of() : dishes;
        }
        public CoachMealRequest(LocalDate date, MealType mealType, Integer calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, LocalTime mealTime, String notes, MealSource source, boolean confirmed) {
            this(date, mealType, calories, proteinGrams, carbohydrateGrams, fatGrams, mealTime, notes, source, confirmed, List.of());
        }
        public MealRequest meal() {
            return new MealRequest(
                date,
                mealType,
                calories,
                proteinGrams,
                carbohydrateGrams,
                fatGrams,
                mealTime,
                notes,
                dishes
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
        MealSource source,
        List<MealDishResponse> dishes
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
                meal.getSource(),
                meal.getDishes().stream().map(MealDishResponse::from).toList()
            );
        }
    }

    public record MealDishResponse(Long id, int position, String name, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams) {
        public static MealDishResponse from(com.jllado.weightcontrol.domain.MealDish dish) {
            return new MealDishResponse(dish.getId(), dish.getPosition(), dish.getName(), dish.getCalories(), dish.getProteinGrams(), dish.getCarbohydrateGrams(), dish.getFatGrams());
        }
    }
}
