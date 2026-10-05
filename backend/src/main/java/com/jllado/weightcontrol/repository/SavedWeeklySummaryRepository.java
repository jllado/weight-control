package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.User;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedWeeklySummaryRepository extends JpaRepository<SavedWeeklySummary, Long> {

    List<SavedWeeklySummary> findByUserOrderByFridayDateDesc(User user);

    Page<SavedWeeklySummary> findByUserOrderByFridayDateDescIdDesc(User user, Pageable pageable);

    long countByUserAndFridayDateAfter(User user, LocalDate fridayDate);

    Optional<SavedWeeklySummary> findByUserAndFridayDate(User user, LocalDate fridayDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select summary from SavedWeeklySummary summary where summary.user = :user and summary.fridayDate = :fridayDate")
    Optional<SavedWeeklySummary> findByUserAndFridayDateForUpdate(@Param("user") User user, @Param("fridayDate") LocalDate fridayDate);

    List<SavedWeeklySummary> findByUserAndFridayDateBetweenOrderByFridayDateAsc(User user, LocalDate startDate, LocalDate endDate);

    boolean existsByUserAndFridayDate(User user, LocalDate fridayDate);
}
