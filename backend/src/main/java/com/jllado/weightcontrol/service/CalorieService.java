package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private List<DailyCalories> aggregate(List<Meal> meals) {
        Map<LocalDate, Integer> totals = meals.stream().collect(Collectors.groupingBy(
            Meal::getMealDate,
            LinkedHashMap::new,
            Collectors.summingInt(Meal::getCalories)
        ));
        return totals.entrySet().stream().map(entry -> new DailyCalories(entry.getKey(), entry.getValue())).toList();
    }

    public record DailyCalories(LocalDate date, int calories) {
    }
}
