package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.DishRecipeDtos.RecipeRequest;
import com.jllado.weightcontrol.api.dto.DishRecipeDtos.RecipeResponse;
import com.jllado.weightcontrol.domain.DishRecipe;
import com.jllado.weightcontrol.domain.RecipeIngredient;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.DishRecipeRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DishRecipeService {
    private final DishRecipeRepository repository;
    public DishRecipeService(DishRecipeRepository repository) { this.repository = repository; }
    public List<RecipeResponse> findAll(User user) { return repository.findByUserOrderByNameAsc(user).stream().map(RecipeResponse::from).toList(); }
    public RecipeResponse find(User user, Long id) { return RecipeResponse.from(requireOwned(user, id)); }
    public RecipeResponse create(User user, RecipeRequest request) {
        var recipe = new DishRecipe();
        recipe.setUser(user);
        apply(recipe, request);
        return RecipeResponse.from(repository.saveAndFlush(recipe));
    }
    public RecipeResponse update(User user, Long id, RecipeRequest request) {
        var recipe = requireOwned(user, id);
        recipe.getIngredients().clear();
        repository.flush();
        apply(recipe, request);
        return RecipeResponse.from(repository.saveAndFlush(recipe));
    }
    public void delete(User user, Long id) { repository.delete(requireOwned(user, id)); }
    private DishRecipe requireOwned(User user, Long id) {
        var recipe = repository.findById(id).orElseThrow(() -> new NotFoundException("Dish not found"));
        if (!recipe.getUser().getId().equals(user.getId())) throw new NotFoundException("Dish not found");
        return recipe;
    }
    private void apply(DishRecipe recipe, RecipeRequest request) {
        String name = request.name().trim();
        String normalizedName = name.toLowerCase(Locale.ROOT);
        repository.findByUserAndNormalizedName(recipe.getUser(), normalizedName)
            .filter(existing -> !existing.getId().equals(recipe.getId()))
            .ifPresent(existing -> { throw new BadRequestException("A dish with this name already exists."); });
        recipe.setName(name);
        recipe.setNormalizedName(normalizedName);
        recipe.setServings(request.servings());
        for (int index = 0; index < request.ingredients().size(); index++) {
            var input = request.ingredients().get(index);
            if (input.quantity() == null || input.unit() == null) throw new BadRequestException("Ingredient quantity and unit are required");
            var food = new RecipeIngredient();
            food.setRecipe(recipe);
            food.setPosition(index + 1);
            food.setName(input.name().trim());
            DishNutrition.apply(food, input);
            recipe.getIngredients().add(food);
        }
    }
}
