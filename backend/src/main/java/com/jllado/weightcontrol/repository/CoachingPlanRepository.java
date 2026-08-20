package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachingPlanRepository extends JpaRepository<CoachingPlan, Long> {
    Optional<CoachingPlan> findByUser(User user);
}
