package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.WorkoutAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkoutAssessmentRepository extends JpaRepository<WorkoutAssessment, Long> {
    Optional<WorkoutAssessment> findByUserAndWorkoutDate(User user, LocalDate date);
    List<WorkoutAssessment> findByUserAndWorkoutDateIn(User user, List<LocalDate> dates);
    void deleteByUserAndWorkoutDate(User user, LocalDate date);
}
