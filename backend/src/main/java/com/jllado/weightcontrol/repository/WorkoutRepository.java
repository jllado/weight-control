package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {
    @EntityGraph(attributePaths = {"lines", "lines.exercise", "assessment"})
    List<Workout> findByUserOrderByWorkoutDateDesc(User user);

    Optional<Workout> findFirstByUserOrderByWorkoutDateAsc(User user);

    Optional<Workout> findFirstByUserOrderByWorkoutDateDesc(User user);

    @EntityGraph(attributePaths = {"lines", "lines.exercise", "assessment"})
    List<Workout> findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(User user, LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = {"lines", "lines.exercise", "assessment"})
    List<Workout> findByUserAndWorkoutDateIn(User user, List<LocalDate> workoutDates);

    @EntityGraph(attributePaths = {"lines", "lines.exercise", "assessment"})
    List<Workout> findTop10ByUserAndWorkoutDateBeforeOrderByWorkoutDateDesc(User user, LocalDate workoutDate);

    Optional<Workout> findByUserAndWorkoutDate(User user, LocalDate workoutDate);

    @EntityGraph(attributePaths = {"lines", "lines.exercise", "assessment"})
    Optional<Workout> findWithLinesById(Long id);

    @EntityGraph(attributePaths = {"lines", "lines.exercise", "assessment"})
    Optional<Workout> findWithLinesByUserAndWorkoutDate(User user, LocalDate workoutDate);

    long countByUser(User user);
}
