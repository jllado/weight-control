package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WorkoutPlan;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {
    Optional<WorkoutPlan> findByUserAndArchivedAtIsNull(User user);
    Optional<WorkoutPlan> findByIdAndUser(Long id, User user);
    Page<WorkoutPlan> findByUserAndArchivedAtIsNotNullOrderByArchivedAtDescIdDesc(User user, Pageable pageable);
}
