package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Calorie;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalorieRepository extends JpaRepository<Calorie, Long> {

    List<Calorie> findByUserOrderByCalorieDateDesc(User user);

    List<Calorie> findByUserAndCalorieDateBetweenOrderByCalorieDateAsc(User user, LocalDate startDate, LocalDate endDate);

    Optional<Calorie> findByUserAndCalorieDate(User user, LocalDate calorieDate);
}
