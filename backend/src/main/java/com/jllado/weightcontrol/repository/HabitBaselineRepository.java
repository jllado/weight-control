package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.HabitBaseline;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitBaselineRepository extends JpaRepository<HabitBaseline, Long> {
    Optional<HabitBaseline> findByHabit(Habit habit);
}
