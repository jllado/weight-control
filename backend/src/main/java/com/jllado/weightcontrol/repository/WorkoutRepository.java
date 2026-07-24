package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {
    @EntityGraph(attributePaths = {"lines", "lines.exercise"})
    List<Workout> findByUserOrderByWorkoutDateDesc(User user);

    @EntityGraph(attributePaths = {"lines", "lines.exercise"})
    List<Workout> findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(User user, LocalDate startDate, LocalDate endDate);

    Optional<Workout> findByUserAndWorkoutDate(User user, LocalDate workoutDate);

    @EntityGraph(attributePaths = {"lines", "lines.exercise"})
    Optional<Workout> findWithLinesById(Long id);
}
