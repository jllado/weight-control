package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.HabitCheckin;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitCheckinRepository extends JpaRepository<HabitCheckin, Long> {
    List<HabitCheckin> findByHabitOrderByCheckinDateAscIdAsc(Habit habit);
    boolean existsByHabitAndCheckinDate(Habit habit, LocalDate date);
    Optional<HabitCheckin> findByHabitAndCheckinDate(Habit habit, LocalDate date);
}
