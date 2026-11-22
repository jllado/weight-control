package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.CatalogFoodRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class CatalogFoodService {
    private final CatalogFoodRepository repository;
    private final UserRepository users;

    public CatalogFoodService(CatalogFoodRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    java.util.Optional<CatalogFood> findActiveByName(User user, String name) {
        return repository.findByUserAndNormalizedName(user, normalized(name)).filter(food -> !food.isDeleted());
    }

    public long count(User user) { return repository.countByUserAndDeletedFalse(user); }
    public List<CatalogFoodResponse> findAll(User user) {
        return repository.findByUserAndDeletedFalseOrderByNameAsc(user).stream().map(CatalogFoodResponse::from).toList();
    }
    public record CoachFoodSearch(String query, List<CatalogFoodResponse> foods, boolean hasMore, boolean preview) {}

    public CoachFoodSearch searchForCoach(User user, String foodQuery) {
        String query = foodQuery == null ? null : normalized(foodQuery);
        if (query != null && (query.length() < 2 || query.length() > 80)) {
            throw new BadRequestException("Food query must be between 2 and 80 characters");
        }
        var limit = PageRequest.of(0, 21);
        var matches = query == null
            ? repository.findByUserAndDeletedFalseOrderByNameAscIdAsc(user, limit)
            : repository.findByUserAndDeletedFalseAndNormalizedNameContainingOrderByNameAscIdAsc(user, query, limit);
        return new CoachFoodSearch(
            query,
            matches.stream().limit(20).map(CatalogFoodResponse::from).toList(),
            matches.size() > 20,
            query == null
        );
    }
    public CatalogFoodResponse create(User user, MealDishRequest request) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
        var food = repository.findByUserAndNormalizedName(user, normalized(request.name())).orElseGet(() -> newFood(user));
        if (food.getId() != null) {
            if (!food.isDeleted()) throw new BadRequestException("A food with this name already exists.");
            FoodSnapshotCompatibility.requireAwareReplacement(request.nutrientSchemaVersion(), List.of(food));
        }
        apply(food, request);
        return CatalogFoodResponse.from(repository.saveAndFlush(food));
    }
    public CatalogFoodResponse update(User user, Long id, MealDishRequest request) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
        var food = requireOwned(user, id);
        FoodSnapshotCompatibility.requireAwareReplacement(request.nutrientSchemaVersion(), List.of(food));
        String nextName = normalized(request.name());
        CatalogFood oldName = null;
        if (!food.getNormalizedName().equals(nextName)) {
            var existing = repository.findByUserAndNormalizedName(user, nextName);
            if (existing.isPresent()) {
                if (!existing.get().isDeleted()) throw new BadRequestException("A food with this name already exists.");
                FoodSnapshotCompatibility.requireAwareReplacement(request.nutrientSchemaVersion(), List.of(existing.get()));
                repository.delete(existing.get());
                repository.flush();
            }
            oldName = newFood(user);
            org.springframework.beans.BeanUtils.copyProperties(food, oldName, FoodPortion.class);
            oldName.setNormalizedName(food.getNormalizedName());
            oldName.setDeleted(true);
        }
        apply(food, request);
        repository.saveAndFlush(food);
        if (oldName != null) repository.saveAndFlush(oldName);
        return CatalogFoodResponse.from(food);
    }
    public void delete(User user, Long id) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
        requireOwned(user, id).setDeleted(true);
    }

    // A concurrent meal may introduce the same name; keep the existing catalog snapshot in either case.
    public void register(User user, List<MealDish> foods) {
        users.findByIdForUpdate(user.getId()).orElseThrow();
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
        food.setFruit(request.fruit());
        DishNutrition.apply(food, request);
    }
}
