package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.repository.DashboardReflectionRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardCoachMetricsServiceTest {

    @Mock
    private DashboardReflectionRepository reflectionRepository;

    @Mock
    private WorkoutRepository workoutRepository;

    @InjectMocks
    private DashboardCoachMetricsService service;

    private final WeeklyMetricsCalculator weeklyMetricsCalculator = new WeeklyMetricsCalculator();

    @Test
    void returnsWeekScoreAndWorkoutTotalsWithoutWarmUps() {
        service = new DashboardCoachMetricsService(reflectionRepository, workoutRepository, weeklyMetricsCalculator);
        User user = new User();
        LocalDate selectedDate = LocalDate.of(2026, 8, 30);
        DashboardReflection rated = reflection(selectedDate, 8);
        DashboardReflection previousRated = reflection(selectedDate.minusWeeks(1), 6);
        Workout workout = workout(selectedDate, ExerciseType.TRAINING, "40", 12, 600, "2.5", 120);
        Workout warmUp = workout(selectedDate, ExerciseType.WARM_UP, "200", 100, 3600, "10", 900);
        Workout previousWorkout = workout(selectedDate.minusWeeks(1), ExerciseType.TRAINING, "30", 10, 300, "1.5", 80);

        when(reflectionRepository.findByUserOrderByReflectionDateDesc(user)).thenReturn(List.of(rated, previousRated));
        when(workoutRepository.findByUserOrderByWorkoutDateDesc(user)).thenReturn(List.of(workout, warmUp, previousWorkout));
        when(reflectionRepository.findByUserAndReflectionDateBetweenOrderByReflectionDateAsc(user, selectedDate.minusDays(1), selectedDate.plusDays(5))).thenReturn(List.of(rated));
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, selectedDate.minusDays(1), selectedDate.plusDays(5))).thenReturn(List.of(workout, warmUp));
        when(reflectionRepository.findByUserAndReflectionDateBetweenOrderByReflectionDateAsc(user, selectedDate.minusDays(8), selectedDate.minusDays(2))).thenReturn(List.of(previousRated));
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, selectedDate.minusDays(8), selectedDate.minusDays(2))).thenReturn(List.of(previousWorkout));

        var response = service.get(user, selectedDate, DashboardCoachMetricsService.ChartPeriod.ALL);

        assertEquals(2, response.reflections().size());
        assertEquals(8, response.selectedWeek().reflections().getFirst().planProgressScore());
        assertEquals(6, response.previousWeek().reflections().getFirst().planProgressScore());
        assertEquals(1, response.previousWeek().totals().workoutCount());
        assertEquals(2, response.selectedWeek().totals().workoutCount());
        assertEquals(600, response.selectedWeek().totals().totalDurationSeconds());
        assertEquals(0, new BigDecimal("2.5").compareTo(response.selectedWeek().totals().totalDistanceKm()));
        assertEquals(120, response.selectedWeek().totals().totalCalories());
        assertEquals(0, new BigDecimal("480").compareTo(response.selectedWeek().totals().strengthVolumeKg()));
    }

    private DashboardReflection reflection(LocalDate date, Integer score) {
        DashboardReflection reflection = new DashboardReflection();
        reflection.setReflectionDate(date);
        reflection.setTitle("Weekly review");
        reflection.setPlanProgressScore(score);
        reflection.setPlanProgressRationale(score == null ? null : "Completed planned sessions.");
        return reflection;
    }

    private Workout workout(LocalDate date, ExerciseType type, String weight, int repetitions, int duration, String distance, int calories) {
        Exercise exercise = new Exercise();
        exercise.setName(type == ExerciseType.WARM_UP ? "Warm-up" : "Squat");
        exercise.setExerciseType(type);
        WorkoutSegment segment = new WorkoutSegment();
        segment.setWeight(new BigDecimal(weight));
        segment.setRepetitions(repetitions);
        segment.setDurationSeconds(duration);
        segment.setDistanceKm(new BigDecimal(distance));
        WorkoutLine line = new WorkoutLine();
        line.setExercise(exercise);
        line.setCalories(calories);
        line.setSegments(List.of(segment));
        Workout workout = new Workout();
        workout.setWorkoutDate(date);
        workout.setLines(List.of(line));
        return workout;
    }
}
