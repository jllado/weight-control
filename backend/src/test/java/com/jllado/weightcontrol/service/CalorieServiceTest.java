package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalorieServiceTest {

    @Mock
    private MealRepository repository;

    @InjectMocks
    private CalorieService service;

    @Test
    void findAllAggregatesMealsIntoDailyCalories() {
        User user = new User();
        LocalDate today = LocalDate.of(2026, 8, 14);
        LocalDate yesterday = today.minusDays(1);
        when(repository.findByUserOrderByMealDateDescIdAsc(user)).thenReturn(List.of(
            meal(today, 900),
            meal(today, 1100),
            meal(yesterday, 0)
        ));

        List<CalorieService.DailyCalories> calories = service.findAll(user);

        assertEquals(List.of(
            new CalorieService.DailyCalories(today, 2000),
            new CalorieService.DailyCalories(yesterday, 0)
        ), calories);
    }

    @Test
    void findBetweenPreservesAscendingRepositoryOrder() {
        User user = new User();
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 2);
        when(repository.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, start, end)).thenReturn(List.of(
            meal(start, 800),
            meal(start, 1200),
            meal(end, 2100)
        ));

        assertEquals(List.of(
            new CalorieService.DailyCalories(start, 2000),
            new CalorieService.DailyCalories(end, 2100)
        ), service.findBetween(user, start, end));
    }

    private Meal meal(LocalDate date, int calories) {
        Meal meal = new Meal();
        meal.setMealDate(date);
        meal.setCalories(calories);
        return meal;
    }
}
