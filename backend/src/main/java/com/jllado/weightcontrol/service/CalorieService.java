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
import java.util.Objects;
import java.util.function.Function;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class CalorieService {

    private final MealRepository repository;

    public CalorieService(MealRepository repository) {
        this.repository = repository;
    }

    public List<DailyCalories> findAll(User user) {
        return aggregate(repository.findByUserOrderByMealDateDescIdAsc(user));
    }

    public List<DailyCalories> findBetween(User user, LocalDate startDate, LocalDate endDate) {
        return aggregate(repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, startDate, endDate));
    }

    public long countRecords(User user) {
        return repository.countByUser(user);
    }

    public Optional<LocalDate> findFirstRecordedDate(User user) {
        return repository.findFirstByUserOrderByMealDateAscIdAsc(user).map(Meal::getMealDate);
    }

    public Optional<LocalDate> findLastRecordedDate(User user) {
        return repository.findFirstByUserOrderByMealDateDescIdDesc(user).map(Meal::getMealDate);
    }

    private List<DailyCalories> aggregate(List<Meal> meals) {
        Map<LocalDate, List<Meal>> mealsByDate = meals.stream().collect(Collectors.groupingBy(
            Meal::getMealDate,
            LinkedHashMap::new,
            Collectors.toList()
        ));
        return mealsByDate.entrySet().stream().map(entry -> {
            List<Meal> dayMeals = entry.getValue();
            boolean macrosComplete = dayMeals.stream().allMatch(meal ->
                meal.getProteinGrams() != null
                    && meal.getCarbohydrateGrams() != null
                    && meal.getFatGrams() != null
            );
            return new DailyCalories(
                entry.getKey(),
                dayMeals.stream().mapToInt(Meal::getCalories).sum(),
                macrosComplete ? totalRecorded(dayMeals, Meal::getProteinGrams) : null,
                macrosComplete ? totalRecorded(dayMeals, Meal::getCarbohydrateGrams) : null,
                macrosComplete ? totalRecorded(dayMeals, Meal::getFatGrams) : null
            );
        }).toList();
    }

    private BigDecimal totalRecorded(List<Meal> meals, Function<Meal, BigDecimal> value) {
        List<BigDecimal> values = meals.stream().map(value).filter(Objects::nonNull).toList();
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record DailyCalories(
        LocalDate date,
        int calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams
    ) {
        public DailyCalories(LocalDate date, int calories) {
            this(date, calories, null, null, null);
        }
    }
}
