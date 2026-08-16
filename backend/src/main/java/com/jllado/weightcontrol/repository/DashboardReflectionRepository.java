package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardReflectionRepository extends JpaRepository<DashboardReflection, Long> {

    Optional<DashboardReflection> findByUserAndReflectionDate(User user, LocalDate reflectionDate);

    List<DashboardReflection> findByUserOrderByReflectionDateDesc(User user);

    List<DashboardReflection> findByUserAndReflectionDateBetweenOrderByReflectionDateAsc(User user, LocalDate startDate, LocalDate endDate);

    Optional<DashboardReflection> findFirstByUserOrderByReflectionDateAsc(User user);

    Optional<DashboardReflection> findFirstByUserOrderByReflectionDateDesc(User user);

    List<DashboardReflection> findTop7ByUserAndReflectionDateBeforeOrderByReflectionDateDesc(User user, LocalDate reflectionDate);

    long countByUser(User user);
}
