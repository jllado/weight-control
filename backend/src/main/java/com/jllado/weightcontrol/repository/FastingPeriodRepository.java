package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.FastingPeriodSource;
import com.jllado.weightcontrol.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FastingPeriodRepository extends JpaRepository<FastingPeriod, Long> {

    List<FastingPeriod> findByUserOrderByStartTimeDescIdDesc(User user);

    @Query("""
        select period from FastingPeriod period
        where period.user = :user
          and period.startTime < :endExclusive
          and (period.endTime is null or period.endTime > :startInclusive)
        order by period.startTime asc, period.id asc
        """)
    List<FastingPeriod> findBetween(User user, OffsetDateTime endExclusive, OffsetDateTime startInclusive);

    void deleteByUserAndSource(User user, FastingPeriodSource source);

    Optional<FastingPeriod> findFirstByUserOrderByStartTimeAscIdAsc(User user);

    Optional<FastingPeriod> findFirstByUserOrderByEndTimeDescIdDesc(User user);

    Optional<FastingPeriod> findFirstByUserAndSourceAndEndTimeIsNullOrderByStartTimeDescIdDesc(User user, FastingPeriodSource source);

    long countByUser(User user);

    @Query("""
        select count(period) > 0
        from FastingPeriod period
        where period.user = :user
          and period.startTime < :endTime
          and period.endTime > :startTime
          and period.source = com.jllado.weightcontrol.domain.FastingPeriodSource.MANUAL
          and (:excludedId is null or period.id <> :excludedId)
        """)
    boolean existsOverlapping(User user, OffsetDateTime startTime, OffsetDateTime endTime, Long excludedId);
}
