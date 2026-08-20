package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthConstraintRepository extends JpaRepository<HealthConstraint, Long> {
    List<HealthConstraint> findByUserOrderByActiveDescStartDateDescIdDesc(User user);
    Optional<HealthConstraint> findFirstByUserOrderByStartDateAscIdAsc(User user);
    Optional<HealthConstraint> findFirstByUserOrderByStartDateDescIdDesc(User user);
    long countByUser(User user);

    @Query("""
        select healthConstraint
        from HealthConstraint healthConstraint
        where healthConstraint.user = :user
          and healthConstraint.active = true
          and healthConstraint.startDate <= :to
          and (healthConstraint.endDate is null or healthConstraint.endDate >= :from)
        order by healthConstraint.startDate asc, healthConstraint.id asc
        """)
    List<HealthConstraint> findActiveOverlapping(
        @Param("user") User user,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
