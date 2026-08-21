package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FastingPeriodRepository extends JpaRepository<FastingPeriod, Long> {

    List<FastingPeriod> findByUserOrderByStartTimeDescIdDesc(User user);

    List<FastingPeriod> findByUserAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAscIdAsc(
        User user,
        OffsetDateTime endExclusive,
        OffsetDateTime startInclusive
    );

    Optional<FastingPeriod> findFirstByUserOrderByStartTimeAscIdAsc(User user);

    Optional<FastingPeriod> findFirstByUserOrderByEndTimeDescIdDesc(User user);

    long countByUser(User user);

    @Query("""
        select count(period) > 0
        from FastingPeriod period
        where period.user = :user
          and period.startTime < :endTime
          and period.endTime > :startTime
          and (:excludedId is null or period.id <> :excludedId)
        """)
    boolean existsOverlapping(User user, OffsetDateTime startTime, OffsetDateTime endTime, Long excludedId);
}
