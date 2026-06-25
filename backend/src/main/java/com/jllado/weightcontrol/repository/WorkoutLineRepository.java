package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import com.jllado.weightcontrol.domain.WorkoutLine;

public interface WorkoutLineRepository extends JpaRepository<WorkoutLine, Long> {
    boolean existsByExercise(Exercise exercise);
}
