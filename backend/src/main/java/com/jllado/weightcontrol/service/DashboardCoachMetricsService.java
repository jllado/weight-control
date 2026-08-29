package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.CoachWeekResponse;
import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.DashboardCoachMetricsResponse;
import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.ReflectionMetricResponse;
import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.WeeklyWorkoutMetricResponse;
import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.WorkoutMetricResponse;
import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.WorkoutTotalsResponse;
import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.repository.DashboardReflectionRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.service.WeeklyMetrics.WorkoutSummary;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class DashboardCoachMetricsService {

    public enum ChartPeriod {MONTHLY, LAST_YEAR, ALL}

    private final DashboardReflectionRepository reflectionRepository;
    private final WorkoutRepository workoutRepository;
    private final WeeklyMetricsCalculator weeklyMetricsCalculator;

    public DashboardCoachMetricsService(
        DashboardReflectionRepository reflectionRepository,
        WorkoutRepository workoutRepository,
        WeeklyMetricsCalculator weeklyMetricsCalculator
    ) {
        this.reflectionRepository = reflectionRepository;
        this.workoutRepository = workoutRepository;
        this.weeklyMetricsCalculator = weeklyMetricsCalculator;
    }

    public DashboardCoachMetricsResponse get(User user, LocalDate selectedDate, ChartPeriod period) {
        LocalDate selectedWeekStart = DateTimes.startOfDashboardWeek(selectedDate);
        LocalDate selectedWeekEnd = selectedWeekStart.plusDays(6);
        LocalDate periodStart = switch (period) {
            case MONTHLY -> LocalDate.now(DateTimes.USER_ZONE).minusMonths(3);
            case LAST_YEAR -> LocalDate.now(DateTimes.USER_ZONE).minusYears(1);
            case ALL -> null;
        };
        List<DashboardReflection> periodReflections = periodStart == null
            ? reflectionRepository.findByUserOrderByReflectionDateDesc(user)
            : reflectionRepository.findByUserAndReflectionDateBetweenOrderByReflectionDateAsc(user, periodStart, LocalDate.now(DateTimes.USER_ZONE));
        List<Workout> periodWorkouts = periodStart == null
            ? workoutRepository.findByUserOrderByWorkoutDateDesc(user)
            : workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, periodStart, LocalDate.now(DateTimes.USER_ZONE));
        List<DashboardReflection> selectedReflections = reflectionRepository.findByUserAndReflectionDateBetweenOrderByReflectionDateAsc(user, selectedWeekStart, selectedWeekEnd);
        List<Workout> selectedWorkouts = workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, selectedWeekStart, selectedWeekEnd);
        return new DashboardCoachMetricsResponse(
            new CoachWeekResponse(
                selectedWeekStart,
                selectedWeekEnd,
                selectedReflections.stream().map(this::toReflection).toList(),
                selectedWorkouts.stream().map(this::toWorkout).toList(),
                toTotals(weeklyMetricsCalculator.summarizeWorkouts(selectedWorkouts))
            ),
            periodReflections.stream().filter(reflection -> reflection.getPlanProgressScore() != null).map(this::toReflection).toList(),
            periodWorkouts.stream().map(this::toWorkout).toList(),
            weeklyTotals(periodWorkouts)
        );
    }

    private List<WeeklyWorkoutMetricResponse> weeklyTotals(List<Workout> workouts) {
        Map<LocalDate, List<Workout>> byWeek = workouts.stream()
            .collect(Collectors.groupingBy(workout -> DateTimes.startOfDashboardWeek(workout.getWorkoutDate())));
        return byWeek.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(entry -> new WeeklyWorkoutMetricResponse(entry.getKey(), entry.getKey().plusDays(6), toTotals(weeklyMetricsCalculator.summarizeWorkouts(entry.getValue()))))
            .toList();
    }

    private ReflectionMetricResponse toReflection(DashboardReflection reflection) {
        return new ReflectionMetricResponse(reflection.getReflectionDate(), reflection.getTitle(), reflection.getPlanProgressScore(), reflection.getPlanProgressRationale());
    }

    private WorkoutMetricResponse toWorkout(Workout workout) {
        var assessment = workout.getAssessment();
        return new WorkoutMetricResponse(
            workout.getWorkoutDate(),
            DateTimes.formatDate(workout.getWorkoutDate()),
            workout.getLines().stream().map(line -> line.getExercise().getName()).collect(Collectors.joining(", ")),
            assessment == null ? null : assessment.getGoalAlignmentScore(),
            assessment == null ? null : assessment.getEstimatedTrainingDemandScore(),
            assessment != null && !assessment.getWorkoutUpdatedAt().equals(workout.getUpdatedAt()),
            toTotals(weeklyMetricsCalculator.summarizeWorkouts(List.of(workout)))
        );
    }

    private WorkoutTotalsResponse toTotals(WorkoutSummary summary) {
        return new WorkoutTotalsResponse(summary.workoutCount(), summary.totalDurationSeconds(), summary.totalDistanceKm(), summary.totalCalories(), summary.strengthVolumeKg());
    }
}
