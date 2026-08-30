package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.CoachWeekResponse;
import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.DashboardCoachMetricsResponse;
import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.PlanProgressTrendResponse;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
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
        CoachWeekResponse selectedWeek = week(user, selectedWeekStart);
        CoachWeekResponse previousWeek = week(user, selectedWeekStart.minusWeeks(1));
        return new DashboardCoachMetricsResponse(
            selectedWeek,
            previousWeek,
            week(user, selectedWeekStart, selectedDate),
            week(user, selectedWeekStart.minusWeeks(1), selectedDate.minusWeeks(1)),
            planProgressTrend(user, selectedDate),
            periodReflections.stream().filter(reflection -> reflection.getPlanProgressScore() != null).map(this::toReflection).toList(),
            periodWorkouts.stream().map(this::toWorkout).toList(),
            weeklyTotals(periodWorkouts)
        );
    }

    private PlanProgressTrendResponse planProgressTrend(User user, LocalDate selectedDate) {
        List<DashboardReflection> ratedReflections = reflectionRepository.findByUserAndReflectionDateLessThanEqualOrderByReflectionDateDesc(user, selectedDate).stream()
            .filter(reflection -> reflection.getPlanProgressScore() != null)
            .toList();
        return new PlanProgressTrendResponse(
            ratedReflections.isEmpty() ? null : ratedReflections.getFirst().getPlanProgressScore(),
            ratedReflections.size() < 2 ? null : ratedReflections.get(1).getPlanProgressScore(),
            averagePlanProgress(ratedReflections, selectedDate.minusDays(29), selectedDate),
            averagePlanProgress(ratedReflections, selectedDate.minusDays(59), selectedDate.minusDays(30))
        );
    }

    private BigDecimal averagePlanProgress(List<DashboardReflection> reflections, LocalDate startDate, LocalDate endDate) {
        List<DashboardReflection> inPeriod = reflections.stream()
            .filter(reflection -> !reflection.getReflectionDate().isBefore(startDate) && !reflection.getReflectionDate().isAfter(endDate))
            .toList();
        if (inPeriod.isEmpty()) {
            return null;
        }
        int scoreTotal = inPeriod.stream().mapToInt(DashboardReflection::getPlanProgressScore).sum();
        return BigDecimal.valueOf(scoreTotal).divide(BigDecimal.valueOf(inPeriod.size()), 1, RoundingMode.HALF_UP);
    }

    private CoachWeekResponse week(User user, LocalDate startDate) {
        return week(user, startDate, startDate.plusDays(6));
    }

    private CoachWeekResponse week(User user, LocalDate startDate, LocalDate endDate) {
        List<DashboardReflection> reflections = reflectionRepository.findByUserAndReflectionDateBetweenOrderByReflectionDateAsc(user, startDate, endDate);
        List<Workout> workouts = workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, startDate, endDate);
        return new CoachWeekResponse(
            startDate,
            endDate,
            reflections.stream().map(this::toReflection).toList(),
            workouts.stream().map(this::toWorkout).toList(),
            toTotals(weeklyMetricsCalculator.summarizeWorkouts(workouts))
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
            toTotals(weeklyMetricsCalculator.summarizeWorkouts(List.of(workout)))
        );
    }

    private WorkoutTotalsResponse toTotals(WorkoutSummary summary) {
        return new WorkoutTotalsResponse(summary.workoutCount(), summary.totalDurationSeconds(), summary.totalDistanceKm(), summary.totalCalories(), summary.strengthVolumeKg());
    }
}
