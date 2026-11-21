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
    @Query("""
        select p from WorkoutPlan p left join fetch p.previousVersions
        where p.user = :user and p.createdAt < :dayEnd and (p.archivedAt is null or p.archivedAt > :dayStart)
          and (p.startDate <= :date or exists (select 1 from WorkoutPlan old join old.previousVersions v
                                               where old = p and v.startDate <= :date))
        order by p.id
        """)
    List<WorkoutPlan> findAssessmentCandidates(User user, LocalDate date, Instant dayStart, Instant dayEnd);
    @Query("select p.reviewDate from WorkoutPlan p where p.user = :user and p.archivedAt is null and p.reviewDate <= :today")
    Optional<LocalDate> findDueReviewDate(User user, LocalDate today);

    Optional<WorkoutPlan> findByUserAndArchivedAtIsNull(User user);
    Optional<WorkoutPlan> findByIdAndUser(Long id, User user);
    Page<WorkoutPlan> findByUserAndArchivedAtIsNotNullOrderByArchivedAtDescIdDesc(User user, Pageable pageable);
}
