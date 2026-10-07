package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.OverallProgressDtos.MetricContribution;
import com.jllado.weightcontrol.api.dto.OverallProgressDtos.OverallProgressResponse;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.*;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class OverallProgressService {
    private static final List<MetricSpec> SPECS = List.of(
        new MetricSpec("Routine completion", 30, 5), new MetricSpec("Body fat", 20, 1),
        new MetricSpec("Blood pressure", 15, 1), new MetricSpec("Sleep quality", 15, 1),
        new MetricSpec("Muscle mass", 10, .5), new MetricSpec("Workout performance", 10, 10)
    );

    private final DailyStatusRepository statusRepository;
    private final WeightRepository weightRepository;
    private final BloodPressureRepository pressureRepository;
    private final SleepRepository sleepRepository;
    private final WorkoutRepository workoutRepository;

    public OverallProgressService(DailyStatusRepository statusRepository, WeightRepository weightRepository,
                                  BloodPressureRepository pressureRepository, SleepRepository sleepRepository,
                                  WorkoutRepository workoutRepository) {
        this.statusRepository = statusRepository;
        this.weightRepository = weightRepository;
        this.pressureRepository = pressureRepository;
        this.sleepRepository = sleepRepository;
        this.workoutRepository = workoutRepository;
    }

    public OverallProgressResponse get(User user, LocalDate selectedDate) {
        LocalDate lastCompleted = LocalDate.now(DateTimes.USER_ZONE).minusDays(1);
        LocalDate end = selectedDate.isAfter(lastCompleted) ? lastCompleted : selectedDate;
        LocalDate currentStart = end.minusDays(29), previousEnd = currentStart.minusDays(1), previousStart = previousEnd.minusDays(29);
        var current = window(user, currentStart, end);
        var previous = window(user, previousStart, previousEnd);
        List<MetricContribution> contributions = new ArrayList<>();
        add(contributions, SPECS.get(0), routineChange(current.statuses, previous.statuses));
        add(contributions, SPECS.get(1), measurementChange(current.weights, previous.weights, Weight::getFatPercentage, true));
        add(contributions, SPECS.get(2), pressureChange(current.pressures, previous.pressures));
        add(contributions, SPECS.get(3), sleepChange(current.sleeps, previous.sleeps));
        add(contributions, SPECS.get(4), measurementChange(current.weights, previous.weights, Weight::getMuscle, false));
        add(contributions, SPECS.get(5), workoutChange(current.workouts, previous.workouts));
        BigDecimal totalWeight = contributions.stream().filter(MetricContribution::included).map(MetricContribution::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal score = totalWeight.signum() == 0 ? null : contributions.stream().filter(MetricContribution::included)
            .map(c -> c.normalizedContribution().multiply(c.weight())).reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(totalWeight, 4, RoundingMode.HALF_UP);
        String status = classify(score);
        return new OverallProgressResponse(status, score, currentStart, end, previousStart, previousEnd, List.copyOf(contributions));
    }

    static String classify(BigDecimal score) {
        return score == null ? null : score.compareTo(BigDecimal.ONE) >= 0 ? "STRONGLY_IMPROVING"
            : score.compareTo(new BigDecimal("0.2")) > 0 ? "SLIGHTLY_IMPROVING"
            : score.compareTo(new BigDecimal("-0.2")) >= 0 ? "STABLE"
            : score.compareTo(BigDecimal.ONE.negate()) <= 0 ? "STRONGLY_DECLINING" : "SLIGHTLY_DECLINING";
    }

    private Window window(User user, LocalDate start, LocalDate end) {
        return new Window(
            statusRepository.findByUserAndStatusDateBetweenOrderByStatusDateAsc(user, start, end),
            weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, DateTimes.startOfDay(start), DateTimes.startOfDay(end.plusDays(1))),
            pressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(user, DateTimes.startOfDay(start), DateTimes.startOfDay(end.plusDays(1))),
            sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, start, end),
            workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, start, end)
        );
    }

    private void add(List<MetricContribution> out, MetricSpec spec, MetricResult result) {
        boolean included = result != null;
        BigDecimal raw = included ? BigDecimal.valueOf(result.change) : null;
        BigDecimal normalized = included ? BigDecimal.valueOf(Math.max(-2, Math.min(2, result.change / spec.unit))) : null;
        out.add(new MetricContribution(spec.name, included, BigDecimal.valueOf(spec.weight), raw, normalized,
            included ? result.explanation : resultExplanation(spec.name)));
    }

    private MetricResult routineChange(List<DailyStatus> current, List<DailyStatus> previous) {
        if (current.size() < 7 || previous.size() < 7) return null;
        double a = current.stream().mapToDouble(s -> s.getRoutinesPercentage().doubleValue()).average().orElseThrow();
        double b = previous.stream().mapToDouble(s -> s.getRoutinesPercentage().doubleValue()).average().orElseThrow();
        return new MetricResult(a - b, "Average routine completion changed by " + round(a - b) + " percentage points.");
    }

    private MetricResult measurementChange(List<Weight> current, List<Weight> previous, Function<Weight, BigDecimal> value, boolean lowerIsBetter) {
        if (distinctDates(current, Weight::getMeasuredAt) < 3 || distinctDates(previous, Weight::getMeasuredAt) < 3) return null;
        double change = average(current, value) - average(previous, value);
        return new MetricResult(lowerIsBetter ? -change : change, "Average " + (lowerIsBetter ? "body-fat percentage" : "muscle mass") + " changed by " + round(change) + (lowerIsBetter ? " percentage points." : " kg."));
    }

    private MetricResult pressureChange(List<BloodPressure> current, List<BloodPressure> previous) {
        if (distinctDates(current, BloodPressure::getMeasuredAt) < 3 || distinctDates(previous, BloodPressure::getMeasuredAt) < 3) return null;
        int before = pressureStage(current), after = pressureStage(previous);
        // A lower stage is favorable; invert so favorable movement has a positive sign.
        return new MetricResult(after - before, "Average blood-pressure stage moved " + (after < before ? "toward the favorable end" : after > before ? "toward the unfavorable end" : "not at all") + ".");
    }

    private int pressureStage(List<BloodPressure> values) {
        int upper = (int) Math.round(values.stream().mapToInt(BloodPressure::getUpper).average().orElseThrow());
        int lower = (int) Math.round(values.stream().mapToInt(BloodPressure::getLower).average().orElseThrow());
        if (upper > 180 || lower > 120) return 4;
        if (upper > 140 || lower > 90) return 3;
        if (upper > 130 || lower > 80) return 2;
        if (upper > 120 && lower <= 80) return 1;
        return 0;
    }

    private MetricResult sleepChange(List<Sleep> current, List<Sleep> previous) {
        List<Sleep> validCurrent = completeSleeps(current), validPrevious = completeSleeps(previous);
        if (validCurrent.stream().map(Sleep::getSleepDate).distinct().count() < 7 || validPrevious.stream().map(Sleep::getSleepDate).distinct().count() < 7) return null;
        double change = averageSleepScore(validCurrent) - averageSleepScore(validPrevious);
        return new MetricResult(change, "Existing four-criterion sleep score changed by " + round(change) + " points.");
    }

    private List<Sleep> completeSleeps(List<Sleep> sleeps) {
        return sleeps.stream().filter(s -> s.getBedtimeStart() != null && s.getBedtimeEnd() != null && s.getTotalSleepDuration() != null).toList();
    }

    private double averageSleepScore(List<Sleep> sleeps) {
        double inBed = sleeps.stream().mapToLong(s -> java.time.Duration.between(s.getBedtimeStart(), s.getBedtimeEnd()).getSeconds()).average().orElse(0);
        double total = sleeps.stream().mapToInt(Sleep::getTotalSleepDuration).average().orElse(0);
        double efficiency = sleeps.stream().mapToDouble(s -> s.getTotalSleepDuration() * 100d / java.time.Duration.between(s.getBedtimeStart(), s.getBedtimeEnd()).getSeconds()).average().orElse(0);
        double[] midpointMinutes = sleeps.stream().mapToDouble(s -> {
            long seconds = java.time.Duration.between(s.getBedtimeStart(), s.getBedtimeEnd()).getSeconds();
            var local = s.getBedtimeStart().atZoneSameInstant(DateTimes.USER_ZONE);
            return (local.getHour() * 60 + local.getMinute() + seconds / 120d) % 1440;
        }).toArray();
        double meanSin = Arrays.stream(midpointMinutes).map(m -> Math.sin(m * 2 * Math.PI / 1440)).average().orElseThrow();
        double meanCos = Arrays.stream(midpointMinutes).map(m -> Math.cos(m * 2 * Math.PI / 1440)).average().orElseThrow();
        double midpoint = (Math.atan2(meanSin, meanCos) * 1440 / (2 * Math.PI) + 1440) % 1440;
        double deviation = Math.sqrt(Arrays.stream(midpointMinutes).map(m -> {
            double d = ((m - midpoint + 720) % 1440 + 1440) % 1440 - 720;
            return d * d;
        }).average().orElseThrow());
        return (inBed >= 25200 && inBed <= 32400 ? 1 : 0) + (total >= 21600 ? 1 : 0) + (efficiency >= 85 ? 1 : 0) + (deviation < 60 ? 1 : 0);
    }

    private MetricResult workoutChange(List<Workout> current, List<Workout> previous) {
        Map<String, Map<LocalDate, Double>> a = workoutSeries(current), b = workoutSeries(previous);
        List<Double> changes = a.entrySet().stream().filter(e -> b.containsKey(e.getKey()))
            .filter(e -> e.getValue().size() >= 2 && b.get(e.getKey()).size() >= 2)
            .map(e -> {
                double before = b.get(e.getKey()).values().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                double after = e.getValue().values().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
                return before > 0 ? (after - before) * 100 / before : Double.NaN;
            }).filter(Double::isFinite).toList();
        if (changes.isEmpty()) return null;
        double change = changes.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        return new MetricResult(change, "Comparable exercise daily bests changed by an average " + round(change) + " percent.");
    }

    private Map<String, Map<LocalDate, Double>> workoutSeries(List<Workout> workouts) {
        Map<String, Map<LocalDate, Double>> series = new HashMap<>();
        for (Workout workout : workouts) for (WorkoutLine line : workout.getLines()) {
            Exercise exercise = line.getExercise();
            if (exercise.getExerciseType() != ExerciseType.TRAINING) continue;
            Map<String, Double> daily = new HashMap<>();
            for (WorkoutSegment segment : line.getSegments()) {
                if (segment.isSkipped()) continue;
                if (exercise.getTrackingMode() == ExerciseTrackingMode.REPS && segment.getWeight() != null && segment.getRepetitions() != null) {
                    daily.merge("repetitions@" + segment.getWeight().stripTrailingZeros().toPlainString(), segment.getRepetitions().doubleValue(), Math::max);
                } else if (exercise.getTrackingMode() == ExerciseTrackingMode.SECONDS && segment.getDurationSeconds() != null) {
                    String load = segment.getWeight() == null ? "0" : segment.getWeight().stripTrailingZeros().toPlainString();
                    daily.merge("duration@" + load, segment.getDurationSeconds().doubleValue(), Math::max);
                } else if (exercise.getTrackingMode() == ExerciseTrackingMode.CARDIO) {
                    if (segment.getDistanceKm() != null) daily.merge("distance", segment.getDistanceKm().doubleValue(), Math::max);
                    if (segment.getDurationSeconds() != null) daily.merge("duration", segment.getDurationSeconds().doubleValue(), Math::max);
                    if (exercise.getCardioMetric() == CardioMetric.SPEED_KPH && segment.getSpeedKph() != null) daily.merge("speed", segment.getSpeedKph().doubleValue(), Math::max);
                    if (exercise.getCardioMetric() == CardioMetric.CADENCE_RPM && segment.getCadenceRpm() != null) daily.merge("cadence", segment.getCadenceRpm().doubleValue(), Math::max);
                }
            }
            daily.forEach((metric, value) -> series.computeIfAbsent(exercise.getId() + ":" + metric, ignored -> new HashMap<>()).merge(workout.getWorkoutDate(), value, Math::max));
        }
        return series;
    }

    private <T> long distinctDates(List<T> values, Function<T, java.time.OffsetDateTime> date) { return values.stream().map(date).map(DateTimes::toLocalDate).distinct().count(); }
    private double average(List<Weight> values, Function<Weight, BigDecimal> field) { return values.stream().map(field).mapToDouble(BigDecimal::doubleValue).average().orElseThrow(); }
    private String resultExplanation(String metric) { return "Not enough observations in both comparison periods."; }
    private double round(double n) { return Math.round(n * 10) / 10d; }

    private record Window(List<DailyStatus> statuses, List<Weight> weights, List<BloodPressure> pressures, List<Sleep> sleeps, List<Workout> workouts) {}
    private record MetricSpec(String name, int weight, double unit) {}
    private record MetricResult(double change, String explanation) {}
}
