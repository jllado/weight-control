package com.jllado.weightcontrol.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
        @NotEmpty List<@NotNull @Valid MealDishRequest> ingredients,
        @Min(2) @Max(2) Integer nutrientSchemaVersion
    ) {
        public RecipeRequest(String name, BigDecimal servings, List<MealDishRequest> ingredients) {
            this(name, servings, ingredients, null);
        }
    }
    public record CoachRecipeRequest(@NotNull @Valid RecipeRequest recipe, boolean confirmed) {
        @JsonIgnore
        @AssertTrue(message = "Coach recipe ingredients require sourced Fiber and complete nutrient references")
        public boolean isIngredientNutritionComplete() {
            if (recipe == null || recipe.ingredients() == null) return true;
            return recipe.ingredients().stream().allMatch(ingredient -> ingredient != null
                && ingredient.fiberGrams() != null && ingredient.fiberSource() != null
                && !ingredient.fiberSource().isBlank() && ingredient.fiberEstimated() != null
                && ingredient.reference() != null && ingredient.reference().fiberGrams() != null
                && ingredient.proteinGrams() != null && ingredient.carbohydrateGrams() != null
                && ingredient.fatGrams() != null && ingredient.reference().proteinGrams() != null
                && ingredient.reference().carbohydrateGrams() != null && ingredient.reference().fatGrams() != null
                && ingredient.reference().vitaminDMicrograms() != null
                && ingredient.reference().magnesiumMilligrams() != null
                && (ingredient.omega3Milligrams() == null || ingredient.reference().omega3Milligrams() != null)
                && (ingredient.epaDhaMilligrams() == null || ingredient.reference().epaDhaMilligrams() != null)
                && (ingredient.alaMilligrams() == null || ingredient.reference().alaMilligrams() != null)
                && CoachDishReferenceConsistency.matches(ingredient));
        }
    }
    public record RecipeResponse(Long id, String name, BigDecimal servings, List<MealDishRequest> ingredients) {
        public static RecipeResponse from(DishRecipe recipe) {
            return new RecipeResponse(recipe.getId(), recipe.getName(), recipe.getServings(), recipe.getIngredients().stream()
                .map(food -> new MealDishRequest(food.getName(), food.getCalories(), food.getProteinGrams(), food.getCarbohydrateGrams(), food.getFatGrams(), food.getQuantity(), food.getUnit(), DishReference.from(food), food.isFruit(),
                    food.getVitaminDMicrograms(), food.getOmega3Milligrams(), food.getMagnesiumMilligrams(), food.getNutrientSource(), food.getNutrientsEstimated(),
                    food.getEpaDhaMilligrams(), food.getEpaDhaSource(), food.getEpaDhaEstimated(),
                    food.getAlaMilligrams(), food.getAlaSource(), food.getAlaEstimated(),
                    food.getFiberGrams(), food.getFiberSource(), food.getFiberEstimated(), 2)).toList());
        }
    }
}
