package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.WorkoutAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutAssessmentRepository extends JpaRepository<WorkoutAssessment, Long> {
    long countByWorkoutId(Long workoutId);
}
