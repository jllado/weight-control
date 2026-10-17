package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyCaloriePlan;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyCaloriePlanRepository extends JpaRepository<WeeklyCaloriePlan, Long> {
    Optional<WeeklyCaloriePlan> findByUserAndWeekStart(User user, LocalDate weekStart);
    List<WeeklyCaloriePlan> findByUserAndWeekStartBetweenOrderByWeekStartAsc(User user, LocalDate from, LocalDate to);
}
