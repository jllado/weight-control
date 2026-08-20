package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
    List<Routine> findByUserOrderByStartDateAsc(User user);
    Optional<Routine> findFirstByUserOrderByStartDateAsc(User user);
    Optional<Routine> findFirstByUserOrderByStartDateDesc(User user);
    Optional<Routine> findFirstByUserAndLastTimeDateIsNotNullOrderByLastTimeDateDesc(User user);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select routine from Routine routine where routine.id = :id")
    Optional<Routine> findByIdForUpdate(@Param("id") Long id);
    long countByUser(User user);
    boolean existsByLegacyFirebaseId(String legacyFirebaseId);
}
