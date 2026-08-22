package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PersonalRecordCalculator {

    public Calculation calculate(List<Weight> weights, List<Workout> workouts) {
        return calculate(new Sources(weights, workouts, List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    public Calculation calculate(Sources sources) {
        return calculate(sources, Map.of());
    }

    public Calculation calculate(Sources sources, Map<PersonalRecordCatalogMetric, PersonalRecordMode> overrides) {
        Map<BaseSeries, List<Observation>> observations = new LinkedHashMap<>();
        addBodyObservations(observations, sources.weights());
        addWorkoutObservations(observations, sources.workouts());
        addBloodPressureObservations(observations, sources.bloodPressures());
        addLipidObservations(observations, sources.lipidPanels());
        addMoodObservations(observations, sources.moods());
        addSleepObservations(observations, sources.sleeps());
        addMealObservations(observations, sources.meals());

        List<CurrentRecord> current = new ArrayList<>();
        List<HistoryEvent> history = new ArrayList<>();
        for (var entry : observations.entrySet()) {
            PersonalRecordMode mode = overrides.getOrDefault(entry.getKey().metric(), entry.getKey().metric().getDefaultMode());
            for (PersonalRecordDirection direction : mode.directions()) {
                calculateSeries(entry.getKey().direction(direction), entry.getValue(), current, history);
            }
        }
        current.sort(currentComparator());
        history.sort(historyComparator());
        return new Calculation(List.copyOf(current), List.copyOf(history));
    }

    private void calculateSeries(Series series, List<Observation> observations, List<CurrentRecord> current, List<HistoryEvent> history) {
        BigDecimal best = null;
        Observation currentSource = null;
        List<HistoryEventDraft> drafts = new ArrayList<>();
        for (Observation observation : observations) {
            if (best == null) {
                best = observation.value();
                currentSource = observation;
                drafts.add(new HistoryEventDraft(observation, PersonalRecordEventKind.FIRST, null));
            } else {
                int comparison = observation.value().compareTo(best);
                if (isBetter(series.metric().getDirection(), comparison)) {
                    BigDecimal previous = best;
                    best = observation.value();
                    currentSource = observation;
                    drafts.add(new HistoryEventDraft(observation, PersonalRecordEventKind.IMPROVED, previous));
                } else if (comparison == 0) {
                    drafts.add(new HistoryEventDraft(observation, PersonalRecordEventKind.TIED, best));
                }
            }
        }
        BigDecimal currentValue = best;
        Observation recordSource = currentSource;
        current.add(new CurrentRecord(series, currentValue, recordSource.date(), recordSource.source()));
        drafts.stream().map(draft -> new HistoryEvent(
            series,
            draft.observation().value(),
            draft.previousValue(),
            draft.observation().date(),
            draft.kind(),
            draft.observation().value().compareTo(currentValue) == 0,
            draft.observation().source()
        )).forEach(history::add);
    }

    private void addBloodPressureObservations(Map<BaseSeries, List<Observation>> observations, List<BloodPressure> sourceReadings) {
        List<BloodPressure> readings = new ArrayList<>(sourceReadings);
        readings.sort(Comparator.comparing(BloodPressure::getMeasuredAt).thenComparing(BloodPressure::getId));
        for (BloodPressure reading : readings) {
            LocalDate date = DateTimes.toLocalDate(reading.getMeasuredAt());
            Source source = new Source(PersonalRecordSourceType.BLOOD_PRESSURE, reading.getId(), null, null);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.BLOOD_PRESSURE_SYSTOLIC, null, null), BigDecimal.valueOf(reading.getUpper()), date, source);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.BLOOD_PRESSURE_DIASTOLIC, null, null), BigDecimal.valueOf(reading.getLower()), date, source);
        }
    }

    private void addLipidObservations(Map<BaseSeries, List<Observation>> observations, List<LipidPanel> sourcePanels) {
        List<LipidPanel> panels = new ArrayList<>(sourcePanels);
        panels.sort(Comparator.comparing(LipidPanel::getPanelDate).thenComparing(LipidPanel::getId));
        for (LipidPanel panel : panels) {
            Source source = new Source(PersonalRecordSourceType.LIPID_PANEL, panel.getId(), null, null);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.LIPID_TOTAL_CHOLESTEROL, null, null), BigDecimal.valueOf(panel.getTotalCholesterol()), panel.getPanelDate(), source);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.LIPID_HDL, null, null), BigDecimal.valueOf(panel.getHdlCholesterol()), panel.getPanelDate(), source);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.LIPID_LDL, null, null), BigDecimal.valueOf(panel.getLdlCholesterol()), panel.getPanelDate(), source);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.LIPID_TRIGLYCERIDES, null, null), BigDecimal.valueOf(panel.getTriglycerides()), panel.getPanelDate(), source);
        }
    }

    private void addMoodObservations(Map<BaseSeries, List<Observation>> observations, List<Mood> sourceMoods) {
        List<Mood> moods = new ArrayList<>(sourceMoods);
        moods.sort(Comparator.comparing(Mood::getMoodDate).thenComparing(Mood::getPeriod).thenComparing(Mood::getId));
        for (Mood mood : moods) {
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.MOOD, null, null), BigDecimal.valueOf(mood.getValue()), mood.getMoodDate(), new Source(PersonalRecordSourceType.MOOD, mood.getId(), null, null));
        }
    }

    private void addSleepObservations(Map<BaseSeries, List<Observation>> observations, List<Sleep> sourceSleeps) {
        List<Sleep> sleeps = new ArrayList<>(sourceSleeps);
        sleeps.sort(Comparator.comparing(Sleep::getSleepDate).thenComparing(Sleep::getId));
        for (Sleep sleep : sleeps) {
            Source source = new Source(PersonalRecordSourceType.SLEEP, sleep.getId(), null, null);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.SLEEP_TOTAL_DURATION, null, null), number(sleep.getTotalSleepDuration()), sleep.getSleepDate(), source);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.SLEEP_DEEP_DURATION, null, null), number(sleep.getDeepSleepDuration()), sleep.getSleepDate(), source);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.SLEEP_REM_DURATION, null, null), number(sleep.getRemSleepDuration()), sleep.getSleepDate(), source);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.SLEEP_LIGHT_DURATION, null, null), number(sleep.getLightSleepDuration()), sleep.getSleepDate(), source);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.SLEEP_AWAKE_TIME, null, null), number(sleep.getAwakeTime()), sleep.getSleepDate(), source);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.SLEEP_AVERAGE_HEART_RATE, null, null), sleep.getAverageHeartRate(), sleep.getSleepDate(), source);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.SLEEP_AVERAGE_HRV, null, null), number(sleep.getAverageHrv()), sleep.getSleepDate(), source);
        }
    }

    private void addMealObservations(Map<BaseSeries, List<Observation>> observations, List<Meal> sourceMeals) {
        List<Meal> meals = new ArrayList<>(sourceMeals);
        meals.sort(Comparator.comparing(Meal::getMealDate).thenComparing(meal -> meal.getMealType().getOrder()).thenComparing(Meal::getMealSequence).thenComparing(Meal::getId));
        for (Meal meal : meals) {
            Source source = new Source(PersonalRecordSourceType.MEAL, meal.getId(), null, null);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.MEAL_CALORIES, null, null), BigDecimal.valueOf(meal.getCalories()), meal.getMealDate(), source);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.MEAL_PROTEIN, null, null), meal.getProteinGrams(), meal.getMealDate(), source);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.MEAL_CARBOHYDRATES, null, null), meal.getCarbohydrateGrams(), meal.getMealDate(), source);
            addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.MEAL_FAT, null, null), meal.getFatGrams(), meal.getMealDate(), source);
        }
        Map<LocalDate, List<Meal>> byDate = new TreeMap<>();
        meals.forEach(meal -> byDate.computeIfAbsent(meal.getMealDate(), ignored -> new ArrayList<>()).add(meal));
        byDate.forEach((date, dailyMeals) -> {
            Source source = new Source(PersonalRecordSourceType.NUTRITION_DAY, null, null, null);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.DAILY_CALORIES, null, null), dailyMeals.stream().map(meal -> BigDecimal.valueOf(meal.getCalories())).reduce(BigDecimal.ZERO, BigDecimal::add), date, source);
            addCompleteDailyMacro(observations, dailyMeals, date, source, PersonalRecordCatalogMetric.DAILY_PROTEIN, Meal::getProteinGrams);
            addCompleteDailyMacro(observations, dailyMeals, date, source, PersonalRecordCatalogMetric.DAILY_CARBOHYDRATES, Meal::getCarbohydrateGrams);
            addCompleteDailyMacro(observations, dailyMeals, date, source, PersonalRecordCatalogMetric.DAILY_FAT, Meal::getFatGrams);
        });
    }

    private void addCompleteDailyMacro(Map<BaseSeries, List<Observation>> observations, List<Meal> meals, LocalDate date, Source source, PersonalRecordCatalogMetric metric, java.util.function.Function<Meal, BigDecimal> getter) {
        if (meals.stream().allMatch(meal -> getter.apply(meal) != null)) {
            add(observations, new BaseSeries(metric, null, null), meals.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add), date, source);
        }
    }

    private BigDecimal number(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private void addBodyObservations(Map<BaseSeries, List<Observation>> observations, List<Weight> sourceWeights) {
        List<Weight> weights = new ArrayList<>(sourceWeights);
        weights.sort(Comparator.comparing(Weight::getMeasuredAt).thenComparing(Weight::getId));
        for (Weight weight : weights) {
            LocalDate date = DateTimes.toLocalDate(weight.getMeasuredAt());
            Source source = new Source(PersonalRecordSourceType.WEIGHT, weight.getId(), null, null);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.BODY_WEIGHT, null, null), weight.getWeight(), date, source);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.BODY_FAT_MASS, null, null), weight.getFat(), date, source);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.BODY_FAT_PERCENTAGE, null, null), weight.getFatPercentage(), date, source);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.BODY_MUSCLE_MASS, null, null), weight.getMuscle(), date, source);
            add(observations, new BaseSeries(PersonalRecordCatalogMetric.BODY_MUSCLE_PERCENTAGE, null, null), weight.getMusclePercentage(), date, source);
        }
    }

    private void addWorkoutObservations(Map<BaseSeries, List<Observation>> observations, List<Workout> sourceWorkouts) {
        List<Workout> workouts = new ArrayList<>(sourceWorkouts);
        workouts.sort(Comparator.comparing(Workout::getWorkoutDate).thenComparing(Workout::getId));
        for (Workout workout : workouts) {
            List<WorkoutLine> lines = new ArrayList<>(workout.getLines());
            lines.sort(Comparator.comparing(WorkoutLine::getPosition));
            for (WorkoutLine line : lines) {
                List<WorkoutSegment> segments = new ArrayList<>(line.getSegments());
                segments.sort(Comparator.comparing(WorkoutSegment::getPosition));
                for (WorkoutSegment segment : segments) {
                    Source source = new Source(PersonalRecordSourceType.WORKOUT, workout.getId(), line.getPosition(), segment.getPosition());
                    addWorkoutSegment(observations, line.getExercise(), segment, workout.getWorkoutDate(), source);
                }
            }
        }
    }

    private void addWorkoutSegment(Map<BaseSeries, List<Observation>> observations, Exercise exercise, WorkoutSegment segment, LocalDate date, Source source) {
        switch (exercise.getTrackingMode()) {
            case REPS -> {
                BigDecimal load = normalizedLoad(segment.getWeight());
                add(observations, new BaseSeries(PersonalRecordCatalogMetric.WORKOUT_HEAVIEST_LOAD, exercise, null), load, date, source);
                add(observations, new BaseSeries(PersonalRecordCatalogMetric.WORKOUT_REPETITIONS, exercise, load), BigDecimal.valueOf(segment.getRepetitions()), date, source);
            }
            case SECONDS -> {
                BigDecimal load = normalizedLoad(segment.getWeight());
                add(observations, new BaseSeries(PersonalRecordCatalogMetric.WORKOUT_HEAVIEST_LOAD, exercise, null), load, date, source);
                add(observations, new BaseSeries(PersonalRecordCatalogMetric.WORKOUT_DURATION, exercise, load), BigDecimal.valueOf(segment.getDurationSeconds()), date, source);
            }
            case CARDIO -> {
                add(observations, new BaseSeries(PersonalRecordCatalogMetric.CARDIO_DURATION, exercise, null), BigDecimal.valueOf(segment.getDurationSeconds()), date, source);
                addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.CARDIO_SPEED, exercise, null), segment.getSpeedKph(), date, source);
                addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.CARDIO_DISTANCE, exercise, null), segment.getDistanceKm(), date, source);
                addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.CARDIO_INCLINE, exercise, null), segment.getInclinePercent(), date, source);
                addOptional(observations, new BaseSeries(PersonalRecordCatalogMetric.CARDIO_RESISTANCE, exercise, null), segment.getResistanceLevel() == null ? null : BigDecimal.valueOf(segment.getResistanceLevel()), date, source);
            }
        }
    }

    private void addOptional(Map<BaseSeries, List<Observation>> observations, BaseSeries series, BigDecimal value, LocalDate date, Source source) {
        if (value != null) {
            add(observations, series, value, date, source);
        }
    }

    private void add(Map<BaseSeries, List<Observation>> observations, BaseSeries series, BigDecimal value, LocalDate date, Source source) {
        observations.computeIfAbsent(series, ignored -> new ArrayList<>()).add(new Observation(value, date, source));
    }

    private BigDecimal normalizedLoad(BigDecimal load) {
        return (load == null ? BigDecimal.ZERO : load).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isBetter(PersonalRecordDirection direction, int comparison) {
        return direction == PersonalRecordDirection.MINIMUM ? comparison < 0 : comparison > 0;
    }

    private Comparator<CurrentRecord> currentComparator() {
        return Comparator.comparing((CurrentRecord record) -> record.series().metric().getDomain())
            .thenComparing(record -> record.series().exercise() == null ? "" : record.series().exercise().getName())
            .thenComparing(record -> record.series().metric())
            .thenComparing(record -> record.series().loadKg(), Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private Comparator<HistoryEvent> historyComparator() {
        return Comparator.comparing(HistoryEvent::date).reversed()
            .thenComparing(event -> event.source().type())
            .thenComparing(event -> event.source().id(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(event -> event.source().linePosition(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(event -> event.source().segmentPosition(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(event -> event.series().metric());
    }

    public record Calculation(List<CurrentRecord> current, List<HistoryEvent> history) {
    }

    public record Sources(List<Weight> weights, List<Workout> workouts, List<BloodPressure> bloodPressures, List<LipidPanel> lipidPanels, List<Mood> moods, List<Sleep> sleeps, List<Meal> meals) {
    }

    public record Series(PersonalRecordMetric metric, Exercise exercise, BigDecimal loadKg) {
        public String key() {
            String key = metric.name();
            if (exercise != null) {
                key += ":" + exercise.getId();
            }
            if (loadKg != null) {
                key += ":" + loadKg.setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
            return key;
        }
    }

    private record BaseSeries(PersonalRecordCatalogMetric metric, Exercise exercise, BigDecimal loadKg) {
        private Series direction(PersonalRecordDirection direction) {
            return new Series(PersonalRecordMetric.forDirection(metric, direction), exercise, loadKg);
        }
    }

    public record Source(PersonalRecordSourceType type, Long id, Integer linePosition, Integer segmentPosition) {
    }

    public record CurrentRecord(Series series, BigDecimal value, LocalDate date, Source source) {
    }

    public record HistoryEvent(Series series, BigDecimal value, BigDecimal previousValue, LocalDate date, PersonalRecordEventKind kind, boolean currentRecord, Source source) {
    }

    private record Observation(BigDecimal value, LocalDate date, Source source) {
    }

    private record HistoryEventDraft(Observation observation, PersonalRecordEventKind kind, BigDecimal previousValue) {
    }
}
