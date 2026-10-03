package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReflectionRepository extends JpaRepository<WeeklyReflection, Long> {

    Optional<WeeklyReflection> findByWeeklySummary(SavedWeeklySummary weeklySummary);

    List<WeeklyReflection> findByWeeklySummaryIn(List<SavedWeeklySummary> weeklySummaries);
}
