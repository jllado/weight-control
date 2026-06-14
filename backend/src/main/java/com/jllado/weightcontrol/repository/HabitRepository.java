package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<Habit, Long> {
    List<Habit> findByUserOrderByStartDateAsc(User user);
    boolean existsByLegacyFirebaseId(String legacyFirebaseId);
}
