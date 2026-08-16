package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineCheckinRepository extends JpaRepository<RoutineCheckin, Long> {
    List<RoutineCheckin> findByRoutineOrderByCheckedAtAsc(Routine routine);
    List<RoutineCheckin> findByRoutineAndCheckedAtBetweenOrderByCheckedAtAsc(Routine routine, OffsetDateTime start, OffsetDateTime end);
    List<RoutineCheckin> findByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(Routine routine, OffsetDateTime startInclusive, OffsetDateTime endExclusive);
    long countByRoutineAndCheckedAtBetween(Routine routine, OffsetDateTime start, OffsetDateTime end);
    boolean existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(Routine routine, OffsetDateTime start, OffsetDateTime end);
    boolean existsByRoutineAndCheckedAt(Routine routine, OffsetDateTime checkedAt);
    Optional<RoutineCheckin> findByRoutineAndCheckedAt(Routine routine, OffsetDateTime checkedAt);
    Optional<RoutineCheckin> findFirstByRoutineOrderByCheckedAtDesc(Routine routine);
    Optional<RoutineCheckin> findFirstByRoutineUserOrderByCheckedAtAsc(User user);
    Optional<RoutineCheckin> findFirstByRoutineUserOrderByCheckedAtDesc(User user);
    long countByRoutineUser(User user);
}
