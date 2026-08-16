package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitRepository extends JpaRepository<Habit, Long> {
    List<Habit> findByUserOrderByStartDateAsc(User user);
    Optional<Habit> findFirstByUserOrderByStartDateAsc(User user);
    Optional<Habit> findFirstByUserOrderByStartDateDesc(User user);
    Optional<Habit> findFirstByUserAndLastTimeDateIsNotNullOrderByLastTimeDateDesc(User user);
    long countByUser(User user);
    boolean existsByLegacyFirebaseId(String legacyFirebaseId);
}
