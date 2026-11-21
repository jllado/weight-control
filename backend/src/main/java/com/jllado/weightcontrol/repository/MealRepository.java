package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealRepository extends JpaRepository<Meal, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "dishes")
    List<Meal> findByUserOrderByMealDateDescIdAsc(User user);

    record Intake(long id, LocalDate mealDate, java.time.LocalTime mealTime, int calories) { }

    List<Intake> findIntakeByUserAndMealDateBetween(User user, LocalDate from, LocalDate to);

    Optional<Meal> findFirstByUserOrderByMealDateAscIdAsc(User user);

    Optional<Meal> findFirstByUserOrderByMealDateDescIdDesc(User user);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "dishes")
    List<Meal> findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(User user, LocalDate startDate, LocalDate endDate);

    List<Meal> findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(User user);

    Optional<Meal> findFirstByUserAndMealTimeIsNotNullOrderByMealDateDescMealTimeDescIdDesc(User user);

    List<Meal> findByUserAndMealDateAndMealTypeOrderByMealSequenceAsc(User user, LocalDate mealDate, MealType mealType);

    Optional<Meal> findByUserAndMealDateAndMealTypeAndMealSequence(User user, LocalDate mealDate, MealType mealType, Integer mealSequence);

    long countByUser(User user);
}
