package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import jakarta.persistence.LockModeType;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    List<Routine> findByUserOrderByStartDateAsc(User user);
    List<Routine> findByReminderTime(LocalTime reminderTime);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select routine from Routine routine where routine.id = :id")
    Optional<Routine> findByIdForUpdate(@Param("id") Long id);
    boolean existsByLegacyFirebaseId(String legacyFirebaseId);
}
