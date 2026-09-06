package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.CatalogFoodRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class CatalogFoodService {
    private final CatalogFoodRepository repository;
    public CatalogFoodService(CatalogFoodRepository repository) { this.repository = repository; }

    public List<CatalogFoodResponse> findAll(User user) {
        return repository.findByUserAndDeletedFalseOrderByNameAsc(user).stream().map(CatalogFoodResponse::from).toList();
    }
    public CatalogFoodResponse create(User user, MealDishRequest request) {
        var food = repository.findByUserAndNormalizedName(user, normalized(request.name())).orElseGet(() -> newFood(user));
        if (food.getId() != null && !food.isDeleted()) throw new BadRequestException("A food with this name already exists.");
        apply(food, request);
        return CatalogFoodResponse.from(repository.saveAndFlush(food));
    }
    public CatalogFoodResponse update(User user, Long id, MealDishRequest request) {
        var food = requireOwned(user, id);
        String nextName = normalized(request.name());
        CatalogFood oldName = null;
        if (!food.getNormalizedName().equals(nextName)) {
            var existing = repository.findByUserAndNormalizedName(user, nextName);
            if (existing.isPresent()) {
                if (!existing.get().isDeleted()) throw new BadRequestException("A food with this name already exists.");
                repository.delete(existing.get());
                repository.flush();
            }
            oldName = newFood(user);
            apply(oldName, new MealDishRequest(food.getName(), food.getCalories(), food.getProteinGrams(), food.getCarbohydrateGrams(), food.getFatGrams(), food.getQuantity(), food.getUnit(), DishReference.from(food)));
            oldName.setDeleted(true);
        }
        apply(food, request);
        repository.saveAndFlush(food);
        if (oldName != null) repository.saveAndFlush(oldName);
        return CatalogFoodResponse.from(food);
    }
    public void delete(User user, Long id) { requireOwned(user, id).setDeleted(true); }

    // A concurrent meal may introduce the same name; keep the existing catalog snapshot in either case.
    public void register(User user, List<MealDish> foods) {
        for (var food : foods) {
            repository.insertIfAbsent(user.getId(), food.getName().trim(), normalized(food.getName()), food);
        }
    }
    private CatalogFood requireOwned(User user, Long id) {
        var food = repository.findById(id).orElseThrow(() -> new NotFoundException("Food not found"));
        if (food.isDeleted() || !food.getUser().getId().equals(user.getId())) throw new NotFoundException("Food not found");
        return food;
    }
    private CatalogFood newFood(User user) { var food = new CatalogFood(); food.setUser(user); return food; }
    private String normalized(String name) { return name.trim().toLowerCase(Locale.ROOT); }
    private void apply(CatalogFood food, MealDishRequest request) {
        if (request.quantity() == null || request.unit() == null) throw new BadRequestException("Food quantity and unit are required");
        food.setName(request.name().trim());
        food.setNormalizedName(normalized(request.name()));
        food.setDeleted(false);
        DishNutrition.apply(food, request);
    }
}
