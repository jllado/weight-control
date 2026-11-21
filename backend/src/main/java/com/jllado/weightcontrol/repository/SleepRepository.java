package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.service.OvernightHeartRateAnalysis.Night;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SleepRepository extends JpaRepository<Sleep, Long> {

    List<Sleep> findByUserOrderBySleepDateDesc(User user);

    @Query("""
        select new com.jllado.weightcontrol.service.OvernightHeartRateAnalysis$Night(s.sleepDate, s.averageHeartRate)
        from Sleep s where s.user = :user and s.sleepDate <= :through and s.averageHeartRate > 0
        order by s.sleepDate
        """)
    List<Night> findHeartRateNights(User user, LocalDate through);

    Optional<Sleep> findFirstByUserOrderBySleepDateAsc(User user);

    Optional<Sleep> findFirstByUserOrderBySleepDateDesc(User user);

    Optional<Sleep> findByUserAndSleepDate(User user, LocalDate sleepDate);

    List<Sleep> findByUserAndSleepDateBetweenOrderBySleepDateAsc(User user, LocalDate startDate, LocalDate endDate);

    List<Sleep> findFirst30ByUserAndAverageHeartRateGreaterThanOrderBySleepDateAsc(User user, BigDecimal minimum);

    long countByUser(User user);
}
