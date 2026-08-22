package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyStatusRepository extends JpaRepository<DailyStatus, Long> {
    Optional<DailyStatus> findByUserAndStatusDate(User user, LocalDate statusDate);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dailyStatus from DailyStatus dailyStatus where dailyStatus.user = :user and dailyStatus.statusDate = :statusDate")
    Optional<DailyStatus> findByUserAndStatusDateForUpdate(@Param("user") User user, @Param("statusDate") LocalDate statusDate);
    Optional<DailyStatus> findFirstByUserOrderByStatusDateAsc(User user);
    Optional<DailyStatus> findFirstByUserOrderByStatusDateDesc(User user);
    List<DailyStatus> findByUserAndStatusDateBetweenOrderByStatusDateAsc(User user, LocalDate startDate, LocalDate endDate);
    Optional<DailyStatus> findFirstByUserAndStatusDateLessThanEqualOrderByStatusDateDesc(User user, LocalDate statusDate);
    long countByUser(User user);
    boolean existsByLegacyFirebaseId(String legacyFirebaseId);
}
