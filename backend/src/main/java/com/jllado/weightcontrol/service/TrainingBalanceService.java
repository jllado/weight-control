package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.TrainingBalanceGroup;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.TrainingBalanceResponse;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TrainingBalanceService {
    private final WorkoutRepository workouts;

    public TrainingBalanceService(WorkoutRepository workouts) { this.workouts = workouts; }

    public TrainingBalanceResponse week(User user, LocalDate date) {
        var start = DateTimes.startOfDashboardWeek(date);
        var end = start.plusDays(6);
        var counts = new EnumMap<PrimaryMuscleGroup, Long>(PrimaryMuscleGroup.class);
        for (var workout : workouts.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, start, end)) {
            for (var line : workout.getLines()) {
                var exercise = line.getExercise();
                if (exercise.getExerciseType() == ExerciseType.TRAINING && exercise.getTrackingMode() != ExerciseTrackingMode.CARDIO) {
                    counts.merge(exercise.getPrimaryMuscleGroup(), (long) line.getSegments().size(), Long::sum);
                }
            }
        }
        var groups = Arrays.stream(PrimaryMuscleGroup.values()).map(group -> new TrainingBalanceGroup(group, counts.getOrDefault(group, 0L))).toList();
        return new TrainingBalanceResponse(start, end, groups.stream().mapToLong(TrainingBalanceGroup::sets).sum(), groups);
    }
}
