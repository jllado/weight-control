package com.jllado.weightcontrol.repository;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {
    @EntityGraph(attributePaths = {"lines", "lines.exercise"})
    @Query("select w from Workout w where w.user = :user order by w.workoutDate desc, case when w.startTime is null then 1 else 0 end, w.startTime, w.createdAt, w.id")
    List<Workout> findByUserOrderByWorkoutDateDesc(User user);

    @Query(value = "select distinct w.workoutDate from Workout w where w.user = :user order by w.workoutDate desc",
        countQuery = "select count(distinct w.workoutDate) from Workout w where w.user = :user")
    Page<LocalDate> findDiaryDates(User user, Pageable pageable);

    @EntityGraph(attributePaths = {"lines", "lines.exercise"})
    @Query("select w from Workout w where w.user = :user and w.id in :ids order by w.workoutDate desc, case when w.startTime is null then 1 else 0 end, w.startTime, w.createdAt, w.id")
    List<Workout> findSessionsByIds(User user, List<Long> ids);

    Optional<Workout> findFirstByUserOrderByWorkoutDateAsc(User user);

    Optional<Workout> findFirstByUserOrderByWorkoutDateDesc(User user);

    @EntityGraph(attributePaths = {"lines", "lines.exercise"})
    @Query("select w from Workout w where w.user = :user and w.workoutDate between :startDate and :endDate order by w.workoutDate, case when w.startTime is null then 1 else 0 end, w.startTime, w.createdAt, w.id")
    List<Workout> findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(User user, LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = {"lines", "lines.exercise"})
    @Query("select w from Workout w where w.user = :user and w.workoutDate in :workoutDates order by w.workoutDate desc, case when w.startTime is null then 1 else 0 end, w.startTime, w.createdAt, w.id")
    List<Workout> findByUserAndWorkoutDateIn(User user, List<LocalDate> workoutDates);

    @Query("select w.id from Workout w where w.user = :user and w.workoutDate <= :through order by w.workoutDate desc, case when w.startTime is null then 1 else 0 end, w.startTime, w.createdAt, w.id")
    List<Long> findPreloadIds(User user, LocalDate through, Pageable pageable);


    @EntityGraph(attributePaths = {"lines", "lines.exercise"})
    Optional<Workout> findWithLinesById(Long id);

    @EntityGraph(attributePaths = {"lines", "lines.exercise"})
    @Query("select w from Workout w where w.user = :user and w.workoutDate = :workoutDate order by case when w.startTime is null then 1 else 0 end, w.startTime, w.createdAt, w.id")
    List<Workout> findSessionsOnDate(User user, LocalDate workoutDate);

    long countByUser(User user);
}
