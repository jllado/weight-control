package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MealService {

    private static final Comparator<Meal> DISPLAY_ORDER = Comparator.comparing(Meal::getMealDate).reversed()
        .thenComparingInt(meal -> meal.getMealType().getOrder())
        .thenComparingInt(Meal::getMealSequence);

    private final MealRepository repository;

    public MealService(MealRepository repository) {
        this.repository = repository;
    }

    public List<Meal> findAll(User user) {
        return repository.findByUserOrderByMealDateDescIdAsc(user).stream().sorted(DISPLAY_ORDER).toList();
    }

    public Meal create(User user, MealRequest request) {
        validateDate(request.date());
        Meal meal = new Meal();
        meal.setUser(user);
        applyIdentity(meal, user, request.date(), request.mealType());
        applyNutrition(meal, request);
        return repository.save(meal);
    }

    public Meal update(User user, Long id, MealRequest request) {
        validateDate(request.date());
        Meal meal = requireOwned(user, id);
        if (!meal.getMealDate().equals(request.date()) || meal.getMealType() != request.mealType()) {
            applyIdentity(meal, user, request.date(), request.mealType());
        }
        applyNutrition(meal, request);
        return repository.save(meal);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    private Meal requireOwned(User user, Long id) {
        Meal meal = repository.findById(id).orElseThrow(() -> new NotFoundException("Meal not found"));
        if (!meal.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Meal not found");
        }
        return meal;
    }

    private void applyIdentity(Meal meal, User user, LocalDate date, MealType type) {
        meal.setMealDate(date);
        meal.setMealType(type);
        meal.setMealSequence(type == MealType.SNACK ? nextSnackSequence(user, date) : 1);
        if (type != MealType.SNACK) {
            repository.findByUserAndMealDateAndMealTypeAndMealSequence(user, date, type, 1)
                .filter(existing -> !existing.getId().equals(meal.getId()))
                .ifPresent(existing -> {
                    throw new BadRequestException("Meal already exists for this date");
                });
        }
    }

    private int nextSnackSequence(User user, LocalDate date) {
        int sequence = 1;
        for (Meal snack : repository.findByUserAndMealDateAndMealTypeOrderByMealSequenceAsc(user, date, MealType.SNACK)) {
            if (snack.getMealSequence() == sequence) {
                sequence++;
            }
        }
        return sequence;
    }

    private void applyNutrition(Meal meal, MealRequest request) {
        meal.setCalories(request.calories());
        meal.setProteinGrams(request.proteinGrams());
        meal.setCarbohydrateGrams(request.carbohydrateGrams());
        meal.setFatGrams(request.fatGrams());
    }

    private void validateDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Meal date cannot be in the future");
        }
    }
}
