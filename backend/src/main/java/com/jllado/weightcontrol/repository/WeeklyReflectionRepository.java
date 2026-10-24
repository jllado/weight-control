package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReflectionRepository extends JpaRepository<WeeklyReflection, Long> {

    Optional<WeeklyReflection> findFirstByWeeklySummaryUserOrderByWeeklySummaryFridayDateDesc(com.jllado.weightcontrol.domain.User user);

    Optional<WeeklyReflection> findByIdAndWeeklySummaryUser(Long id, com.jllado.weightcontrol.domain.User user);

    Optional<WeeklyReflection> findByWeeklySummary(SavedWeeklySummary weeklySummary);

    List<WeeklyReflection> findByWeeklySummaryIn(List<SavedWeeklySummary> weeklySummaries);
}
