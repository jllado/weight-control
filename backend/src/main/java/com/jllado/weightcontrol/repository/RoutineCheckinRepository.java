package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineCheckinRepository extends JpaRepository<RoutineCheckin, Long> {
    List<RoutineCheckin> findByRoutineOrderByCheckedAtAsc(Routine routine);
    List<RoutineCheckin> findByRoutineAndCheckedAtBetweenOrderByCheckedAtAsc(Routine routine, OffsetDateTime start, OffsetDateTime end);
    long countByRoutineAndCheckedAtBetween(Routine routine, OffsetDateTime start, OffsetDateTime end);
    boolean existsByRoutineAndCheckedAt(Routine routine, OffsetDateTime checkedAt);
    Optional<RoutineCheckin> findByRoutineAndCheckedAt(Routine routine, OffsetDateTime checkedAt);
    Optional<RoutineCheckin> findFirstByRoutineOrderByCheckedAtDesc(Routine routine);
}
