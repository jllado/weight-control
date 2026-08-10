package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    List<Routine> findByUserOrderByStartDateAsc(User user);
    List<Routine> findByReminderTime(LocalTime reminderTime);
    boolean existsByLegacyFirebaseId(String legacyFirebaseId);
}
