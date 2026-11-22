package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.DishRecipeDtos.RecipeRequest;
import com.jllado.weightcontrol.api.dto.DishRecipeDtos.RecipeResponse;
import com.jllado.weightcontrol.api.dto.DishRecipeDtos.CoachRecipeRequest;
import com.jllado.weightcontrol.domain.DishRecipe;
import com.jllado.weightcontrol.domain.RecipeIngredient;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.DishRecipeRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DishRecipeService {
    private final DishRecipeRepository repository;
    private final UserRepository users;

    private final CatalogFoodService catalog;

    public DishRecipeService(DishRecipeRepository repository, UserRepository users, CatalogFoodService catalog) {
        this.catalog = catalog;
        this.repository = repository;
        this.users = users;
    }
    public long count(User user) { return repository.countByUser(user); }
    public List<RecipeResponse> findAll(User user) { return repository.findByUserOrderByNameAsc(user).stream().map(RecipeResponse::from).toList(); }
    public RecipeResponse find(User user, Long id) { return RecipeResponse.from(requireOwned(user, id)); }
    public RecipeResponse create(User user, RecipeRequest request) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
        return createLocked(user, request);
    }
    private RecipeResponse createLocked(User user, RecipeRequest request) {
        var recipe = new DishRecipe();
        recipe.setUser(user);
        apply(recipe, request);
        return RecipeResponse.from(repository.saveAndFlush(recipe));
    }
    public RecipeResponse createCoach(User user, CoachRecipeRequest request) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
        return createLocked(user, completeCoachRecipe(user, request.recipe()));
    }
    private RecipeRequest completeCoachRecipe(User user, RecipeRequest request) {
        var ingredients = LegacyCoachSaturatedFat.complete(user, request.nutrientSchemaVersion(), request.ingredients(), catalog);
        return new RecipeRequest(request.name(), request.servings(), ingredients, request.nutrientSchemaVersion());
    }
    public RecipeResponse update(User user, Long id, RecipeRequest request) {
        return update(user, id, request, false);
    }
    public RecipeResponse updateCoach(User user, Long id, CoachRecipeRequest request) {
        return update(user, id, request.recipe(), true);
    }
    private RecipeResponse update(User user, Long id, RecipeRequest request, boolean coach) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
        var recipe = requireOwned(user, id);
        boolean legacyV3 = coach && Integer.valueOf(3).equals(request.nutrientSchemaVersion());
        boolean unchanged = CoachFoodNutrition.sameOrderedFoods(recipe.getIngredients(), request.ingredients(), legacyV3);
        if (!(legacyV3 && unchanged)) FoodSnapshotCompatibility.requireAwareReplacement(request.nutrientSchemaVersion(), recipe.getIngredients());
        if (unchanged) {
            applyMetadata(recipe, request);
        } else {
            if (coach) request = completeCoachRecipe(user, request);
            recipe.getIngredients().clear();
            repository.flush();
            apply(recipe, request);
        }
        return RecipeResponse.from(repository.saveAndFlush(recipe));
    }
    public void delete(User user, Long id) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
        repository.delete(requireOwned(user, id));
    }
    private DishRecipe requireOwned(User user, Long id) {
        var recipe = repository.findById(id).orElseThrow(() -> new NotFoundException("Dish not found"));
        if (!recipe.getUser().getId().equals(user.getId())) throw new NotFoundException("Dish not found");
        return recipe;
    }
    private void applyMetadata(DishRecipe recipe, RecipeRequest request) {
        String name = request.name().trim();
        String normalizedName = name.toLowerCase(Locale.ROOT);
        repository.findByUserAndNormalizedName(recipe.getUser(), normalizedName)
            .filter(existing -> !existing.getId().equals(recipe.getId()))
            .ifPresent(existing -> { throw new BadRequestException("A dish with this name already exists."); });
        recipe.setName(name);
        recipe.setNormalizedName(normalizedName);
        recipe.setServings(request.servings());
    }

    private void apply(DishRecipe recipe, RecipeRequest request) {
        applyMetadata(recipe, request);
        for (int index = 0; index < request.ingredients().size(); index++) {
            var input = request.ingredients().get(index);
            if (input.quantity() == null || input.unit() == null) throw new BadRequestException("Ingredient quantity and unit are required");
            var food = new RecipeIngredient();
            food.setRecipe(recipe);
            food.setPosition(index + 1);
            food.setName(input.name().trim());
            food.setFruit(input.fruit());
            DishNutrition.apply(food, input);
            recipe.getIngredients().add(food);
        }
    }
}
