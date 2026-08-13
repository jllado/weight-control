package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRepository extends JpaRepository<Meal, Long> {

    List<Meal> findByUserOrderByMealDateDescIdAsc(User user);

    List<Meal> findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(User user, LocalDate startDate, LocalDate endDate);

    List<Meal> findByUserAndMealDateAndMealTypeOrderByMealSequenceAsc(User user, LocalDate mealDate, MealType mealType);

    Optional<Meal> findByUserAndMealDateAndMealTypeAndMealSequence(User user, LocalDate mealDate, MealType mealType, Integer mealSequence);
}
