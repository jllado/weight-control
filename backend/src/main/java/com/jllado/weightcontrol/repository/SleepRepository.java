package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SleepRepository extends JpaRepository<Sleep, Long> {

    List<Sleep> findByUserOrderBySleepDateDesc(User user);

    Optional<Sleep> findFirstByUserOrderBySleepDateAsc(User user);

    Optional<Sleep> findFirstByUserOrderBySleepDateDesc(User user);

    Optional<Sleep> findByUserAndSleepDate(User user, LocalDate sleepDate);

    List<Sleep> findByUserAndSleepDateBetweenOrderBySleepDateAsc(User user, LocalDate startDate, LocalDate endDate);

    long countByUser(User user);
}
