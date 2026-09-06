package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.api.dto.MealDtos.DishReference;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.domain.DishRecipe;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class DishRecipeDtos {
    private DishRecipeDtos() { }
    public record RecipeRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Positive @Digits(integer = 8, fraction = 3) BigDecimal servings,
        @NotEmpty List<@NotNull @Valid MealDishRequest> ingredients
    ) { }
    public record RecipeResponse(Long id, String name, BigDecimal servings, List<MealDishRequest> ingredients) {
        public static RecipeResponse from(DishRecipe recipe) {
            return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getServings(), recipe.getIngredients().stream()
                .map(food -> new MealDishRequest(food.getName(), food.getCalories(), food.getProteinGrams(), food.getCarbohydrateGrams(), food.getFatGrams(), food.getQuantity(), food.getUnit(), DishReference.from(food))).toList());
        }
    }
}
