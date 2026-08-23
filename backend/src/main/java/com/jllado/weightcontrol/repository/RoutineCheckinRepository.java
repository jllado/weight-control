package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("""
        select checkin.routine.id
        from RoutineCheckin checkin
        where checkin.routine.id in :routineIds
          and checkin.checkedAt >= :startInclusive
          and checkin.checkedAt < :endExclusive
        group by checkin.routine.id
        """)
    Set<Long> findRoutineIdsWithCheckinsBetween(
        @Param("routineIds") Set<Long> routineIds,
        @Param("startInclusive") OffsetDateTime startInclusive,
        @Param("endExclusive") OffsetDateTime endExclusive
    );
    @Query("""
        select checkin.routine.id as routineId, count(checkin) as checkinCount
        from RoutineCheckin checkin
        where checkin.routine.id in :routineIds
          and checkin.checkedAt between :startInclusive and :endInclusive
        group by checkin.routine.id
        """)
    List<RoutineCheckinCount> countCheckinsByRoutineIdsBetween(
        @Param("routineIds") Set<Long> routineIds,
        @Param("startInclusive") OffsetDateTime startInclusive,
        @Param("endInclusive") OffsetDateTime endInclusive
    );
    long countByRoutineUser(User user);

    interface RoutineCheckinCount {
        Long getRoutineId();
        long getCheckinCount();
    }
}
