package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyStatusRepository extends JpaRepository<DailyStatus, Long> {
    Optional<DailyStatus> findByUserAndStatusDate(User user, LocalDate statusDate);
    Optional<DailyStatus> findFirstByUserOrderByStatusDateAsc(User user);
    Optional<DailyStatus> findFirstByUserOrderByStatusDateDesc(User user);
    List<DailyStatus> findByUserAndStatusDateBetweenOrderByStatusDateAsc(User user, LocalDate startDate, LocalDate endDate);
    Optional<DailyStatus> findFirstByUserAndStatusDateLessThanEqualOrderByStatusDateDesc(User user, LocalDate statusDate);
    boolean existsByLegacyFirebaseId(String legacyFirebaseId);
}
