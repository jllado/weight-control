package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class NutritionService {

    private final MealRepository repository;

    public NutritionService(MealRepository repository) {
        this.repository = repository;
    }

    public List<DailyNutritionSummary> findAll(User user) {
        return aggregate(repository.findByUserOrderByMealDateDescIdAsc(user));
    }

    public List<DailyNutritionSummary> findBetween(User user, LocalDate from, LocalDate to) {
        return aggregate(repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, from, to));
    }

    private List<DailyNutritionSummary> aggregate(List<Meal> meals) {
        Map<LocalDate, List<Meal>> mealsByDate = new LinkedHashMap<>();
        meals.forEach(meal -> mealsByDate.computeIfAbsent(meal.getMealDate(), ignored -> new java.util.ArrayList<>()).add(meal));
        return mealsByDate.entrySet().stream().map(entry -> summarize(entry.getKey(), entry.getValue())).toList();
    }

    private DailyNutritionSummary summarize(LocalDate date, List<Meal> meals) {
        return new DailyNutritionSummary(
            date,
            meals.stream().mapToInt(Meal::getCalories).sum(),
            totalRecorded(meals, Meal::getProteinGrams),
            totalRecorded(meals, Meal::getCarbohydrateGrams),
            totalRecorded(meals, Meal::getFatGrams),
            meals.stream().allMatch(meal ->
                meal.getProteinGrams() != null
                    && meal.getCarbohydrateGrams() != null
                    && meal.getFatGrams() != null
            )
        );
    }

    private BigDecimal totalRecorded(List<Meal> meals, Function<Meal, BigDecimal> value) {
        List<BigDecimal> values = meals.stream().map(value).filter(java.util.Objects::nonNull).toList();
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record DailyNutritionSummary(
        LocalDate date,
        int calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        boolean macrosComplete
    ) {
    }
}
