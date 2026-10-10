package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
        return aggregate(user, repository.findByUserOrderByMealDateDescIdAsc(user));
    }

    public List<DailyNutritionSummary> findBetween(User user, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return aggregate(user, repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, from, to));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || to.isAfter(LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE)) || ChronoUnit.DAYS.between(from, to) >= 90) {
            throw new BadRequestException("Nutrition summaries require a past date range of at most 90 days");
        }
    }

    private List<DailyNutritionSummary> aggregate(User user, List<Meal> meals) {
        Map<LocalDate, List<Meal>> mealsByDate = new LinkedHashMap<>();
        meals.forEach(meal -> mealsByDate.computeIfAbsent(meal.getMealDate(), ignored -> new java.util.ArrayList<>()).add(meal));
        return mealsByDate.entrySet().stream().map(entry -> summarize(user, entry.getKey(), entry.getValue())).toList();
    }

    private DailyNutritionSummary summarize(User user, LocalDate date, List<Meal> meals) {
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
            ),
            NutrientSummaryService.summarize(meals),
            user.getLastCompletedDashboardDate() != null && !date.isAfter(user.getLastCompletedDashboardDate())
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
        boolean macrosComplete,
        com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientSummary nutrients,
        boolean completed) {
        public DailyNutritionSummary(LocalDate date, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, boolean macrosComplete) {
            this(date, calories, proteinGrams, carbohydrateGrams, fatGrams, macrosComplete, null, false);
        }
        public DailyNutritionSummary(LocalDate date, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams, boolean macrosComplete, com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientSummary nutrients) {
            this(date, calories, proteinGrams, carbohydrateGrams, fatGrams, macrosComplete, nutrients, false);
        }

    }
}
