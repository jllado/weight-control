package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageBloodPressure;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageSleep;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageStatus;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageWeight;
import com.jllado.weightcontrol.service.WeeklyMetrics.CalorieSummary;
import com.jllado.weightcontrol.service.WeeklyMetrics.DailyRoutineCompletion;
import com.jllado.weightcontrol.service.WeeklyMetrics.DecisionMetrics;
import com.jllado.weightcontrol.service.WeeklyMetrics.Progress;
import com.jllado.weightcontrol.service.WeeklyMetrics.RoutineCompletion;
import com.jllado.weightcontrol.service.WeeklyMetrics.Summary;
import com.jllado.weightcontrol.service.WeeklyMetrics.WorkoutSummary;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.util.Numbers;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WeeklyMetricsCalculator {

    private static final int YEAR_COMPARISON_WEEKS = 52;

    public Progress progress(User user, LocalDate selectedDate, Input input) {
        LocalDate currentStart = DateTimes.startOfDashboardWeek(selectedDate);
        return new Progress(
            selectedDate.getDayOfWeek() == DayOfWeek.FRIDAY,
            summarize(user, currentStart, selectedDate, input),
            summarize(user, currentStart.minusWeeks(1), selectedDate.minusWeeks(1), input),
            summarize(user, currentStart.minusWeeks(YEAR_COMPARISON_WEEKS), selectedDate.minusWeeks(YEAR_COMPARISON_WEEKS), input)
        );
    }

    public List<Summary> baselineWeeks(User user, LocalDate contextStart, LocalDate baselineEnd, Input input) {
        List<Summary> summaries = new ArrayList<>();
        LocalDate periodStart = contextStart;
        while (!periodStart.isAfter(baselineEnd)) {
            LocalDate dashboardWeekEnd = DateTimes.startOfDashboardWeek(periodStart).plusDays(6);
            LocalDate periodEnd = dashboardWeekEnd.isAfter(baselineEnd) ? baselineEnd : dashboardWeekEnd;
            summaries.add(summarize(user, periodStart, periodEnd, input));
            periodStart = periodEnd.plusDays(1);
        }
        return summaries;
    }

    public Summary summarize(User user, LocalDate start, LocalDate end, Input input) {
        List<DailyStatus> statuses = inRange(input.statuses(), DailyStatus::getStatusDate, start, end);
        List<Weight> weights = inRange(input.weights(), weight -> DateTimes.toLocalDate(weight.getMeasuredAt()), start, end);
        List<BloodPressure> bloodPressures = inRange(input.bloodPressures(), bloodPressure -> DateTimes.toLocalDate(bloodPressure.getMeasuredAt()), start, end);
        List<Mood> moods = inRange(input.moods(), Mood::getMoodDate, start, end);
        List<Sleep> sleeps = inRange(input.sleeps(), Sleep::getSleepDate, start, end);
        List<CalorieService.DailyCalories> calories = inRange(input.calories(), CalorieService.DailyCalories::date, start, end);
        List<Workout> workouts = inRange(input.workouts(), Workout::getWorkoutDate, start, end);
        List<Sickness> sicknesses = inRange(input.sicknesses(), Sickness::getSicknessDate, start, end);
        List<DecisionOutcome> decisions = inRange(input.decisions(), DecisionOutcome::getOutcomeDate, start, end);
        List<RoutineCheckin> checkins = inRange(input.routineCheckins(), checkin -> DateTimes.toLocalDate(checkin.getCheckedAt()), start, end);
        return new Summary(
            start,
            end,
            averageStatus(statuses),
            checkins.size(),
            routineCompletion(statuses),
            averageWeight(weights),
            averageBloodPressure(bloodPressures),
            averageMood(moods),
            (int) moods.stream().map(Mood::getMoodDate).distinct().count(),
            averageSleep(sleeps),
            summarizeCalories(user, start, end, calories),
            summarizeWorkouts(workouts),
            counts(sicknesses, sickness -> sickness.getType().name()),
            counts(sicknesses, sickness -> sickness.getSeverity().name()),
            summarizeDecisions(decisions)
        );
    }

    private RoutineCompletion routineCompletion(List<DailyStatus> statuses) {
        List<DailyRoutineCompletion> days = statuses.stream()
            .map(status -> new DailyRoutineCompletion(
                status.getStatusDate(),
                status.getRoutinesDone(),
                status.getTotalRoutines(),
                percentageOrNull(status.getRoutinesDone(), status.getTotalRoutines())
            ))
            .toList();
        int completed = statuses.stream().mapToInt(DailyStatus::getRoutinesDone).sum();
        int opportunities = statuses.stream().mapToInt(DailyStatus::getTotalRoutines).sum();
        return new RoutineCompletion(completed, opportunities, percentageOrNull(completed, opportunities), days);
    }

    private AverageStatus averageStatus(List<DailyStatus> statuses) {
        if (statuses.isEmpty()) {
            return null;
        }
        return new AverageStatus(
            averageDecimal(statuses.stream().map(DailyStatus::getRoutinesPercentage).toList()),
            averageDecimal(statuses.stream().map(DailyStatus::getWeightPercentage).toList()),
            averageDecimal(statuses.stream().map(DailyStatus::getBloodPressurePercentage).toList()),
            averageDecimal(statuses.stream().map(DailyStatus::getFlexibilityPercentage).toList()),
            averageDecimal(statuses.stream().map(DailyStatus::getMindPercentage).toList())
        );
    }

    private AverageWeight averageWeight(List<Weight> weights) {
        if (weights.isEmpty()) {
            return null;
        }
        return new AverageWeight(
            averageDecimal(weights.stream().map(Weight::getWeight).toList()),
            averageDecimal(weights.stream().map(Weight::getFatPercentage).toList()),
            averageDecimal(weights.stream().map(Weight::getMusclePercentage).toList()),
            weights.size()
        );
    }

    private AverageBloodPressure averageBloodPressure(List<BloodPressure> bloodPressures) {
        if (bloodPressures.isEmpty()) {
            return null;
        }
        return new AverageBloodPressure(
            averageInteger(bloodPressures.stream().map(BloodPressure::getUpper).toList()),
            averageInteger(bloodPressures.stream().map(BloodPressure::getLower).toList()),
            bloodPressures.size()
        );
    }

    private AverageSleep averageSleep(List<Sleep> sleeps) {
        if (sleeps.isEmpty()) {
            return null;
        }
        return new AverageSleep(
            averageInteger(sleeps.stream().map(Sleep::getTotalSleepDuration).toList()),
            averageInteger(sleeps.stream().map(Sleep::getDeepSleepDuration).toList()),
            averageInteger(sleeps.stream().map(Sleep::getRemSleepDuration).toList()),
            averageInteger(sleeps.stream().map(Sleep::getAwakeTime).toList()),
            averageDecimal(sleeps.stream().map(Sleep::getAverageHeartRate).toList()),
            averageInteger(sleeps.stream().map(Sleep::getAverageHrv).toList()),
            sleeps.size()
        );
    }

    private BigDecimal averageMood(List<Mood> moods) {
        List<BigDecimal> dailyAverages = moods.stream()
            .collect(Collectors.groupingBy(Mood::getMoodDate))
            .values().stream()
            .map(day -> BigDecimal.valueOf(day.stream().mapToInt(Mood::getValue).sum())
                .divide(BigDecimal.valueOf(day.size()), 10, RoundingMode.HALF_UP))
            .toList();
        return averageDecimal(dailyAverages);
    }

    private CalorieSummary summarizeCalories(User user, LocalDate start, LocalDate end, List<CalorieService.DailyCalories> calories) {
        int targetTotal = start.datesUntil(end.plusDays(1)).mapToInt(date -> targetCalories(user, date.getDayOfWeek())).sum();
        int total = calories.stream().mapToInt(CalorieService.DailyCalories::calories).sum();
        BigDecimal average = calories.isEmpty() ? null : BigDecimal.valueOf(total)
            .divide(BigDecimal.valueOf(calories.size()), 2, RoundingMode.HALF_UP);
        long days = start.datesUntil(end.plusDays(1)).count();
        BigDecimal targetAverage = BigDecimal.valueOf(targetTotal).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        return new CalorieSummary(calories.size(), total, average, targetAverage, average == null ? null : average.subtract(targetAverage));
    }

    private int targetCalories(User user, DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> user.getTypicalCaloriesSaturday();
            case SUNDAY -> user.getTypicalCaloriesSunday();
            case MONDAY -> user.getTypicalCaloriesMonday();
            case TUESDAY -> user.getTypicalCaloriesTuesday();
            case WEDNESDAY -> user.getTypicalCaloriesWednesday();
            case THURSDAY -> user.getTypicalCaloriesThursday();
            case FRIDAY -> user.getTypicalCaloriesFriday();
        };
    }

    private WorkoutSummary summarizeWorkouts(List<Workout> workouts) {
        int totalDurationSeconds = workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .filter(line -> line.getExercise().getExerciseType() != ExerciseType.WARM_UP)
            .flatMap(line -> line.getSegments().stream())
            .map(WorkoutSegment::getDurationSeconds)
            .filter(java.util.Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
        BigDecimal totalDistanceKm = sumDecimal(workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .filter(line -> line.getExercise().getExerciseType() != ExerciseType.WARM_UP)
            .flatMap(line -> line.getSegments().stream())
            .map(WorkoutSegment::getDistanceKm)
            .toList());
        int totalCalories = workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .filter(line -> line.getExercise().getExerciseType() != ExerciseType.WARM_UP)
            .map(WorkoutLine::getCalories)
            .filter(java.util.Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
        BigDecimal strengthVolumeKg = workouts.stream()
            .flatMap(workout -> workout.getLines().stream())
            .filter(line -> line.getExercise().getExerciseType() != ExerciseType.WARM_UP)
            .flatMap(line -> line.getSegments().stream())
            .filter(segment -> segment.getWeight() != null && segment.getRepetitions() != null)
            .map(segment -> segment.getWeight().multiply(BigDecimal.valueOf(segment.getRepetitions())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WorkoutSummary(workouts.size(), totalDurationSeconds, totalDistanceKm, totalCalories, strengthVolumeKg);
    }

    private DecisionMetrics summarizeDecisions(List<DecisionOutcome> decisions) {
        long wins = decisions.stream().filter(decision -> decision.getOutcome() == DecisionOutcomeType.WIN).count();
        long misses = decisions.size() - wins;
        BigDecimal winRate = decisions.isEmpty() ? null : Numbers.percentage(wins, decisions.size());
        return new DecisionMetrics(wins, misses, winRate);
    }

    private <T> List<T> inRange(List<T> values, Function<T, LocalDate> date, LocalDate start, LocalDate end) {
        return values.stream().filter(value -> !date.apply(value).isBefore(start) && !date.apply(value).isAfter(end)).toList();
    }

    private <T> Map<String, Long> counts(List<T> values, Function<T, String> classifier) {
        return values.stream().collect(Collectors.groupingBy(classifier, Collectors.counting()));
    }

    private BigDecimal averageDecimal(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(java.util.Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return present.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageInteger(List<Integer> values) {
        List<Integer> present = values.stream().filter(java.util.Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(present.stream().mapToInt(Integer::intValue).sum())
            .divide(BigDecimal.valueOf(present.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentageOrNull(int number, int total) {
        return total == 0 ? null : Numbers.percentage(number, total);
    }

    private BigDecimal sumDecimal(List<BigDecimal> values) {
        return values.stream().filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record Input(
        List<DailyStatus> statuses,
        List<Weight> weights,
        List<BloodPressure> bloodPressures,
        List<Mood> moods,
        List<Sleep> sleeps,
        List<CalorieService.DailyCalories> calories,
        List<Workout> workouts,
        List<Sickness> sicknesses,
        List<DecisionOutcome> decisions,
        List<RoutineCheckin> routineCheckins
    ) {
    }
}
