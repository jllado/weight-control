package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WorkoutPlan;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {
    @Query("select p from WorkoutPlan p where p.user = :user and p.startDate <= :date and p.reviewDate >= :date and p.createdAt < :dayEnd and (p.archivedAt is null or p.archivedAt > :dayStart) order by p.id")
    List<WorkoutPlan> findAssessmentCandidates(User user, LocalDate date, Instant dayStart, Instant dayEnd);
    Optional<WorkoutPlan> findByUserAndArchivedAtIsNull(User user);
    Optional<WorkoutPlan> findByIdAndUser(Long id, User user);
    Page<WorkoutPlan> findByUserAndArchivedAtIsNotNullOrderByArchivedAtDescIdDesc(User user, Pageable pageable);
}
