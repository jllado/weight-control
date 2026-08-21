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

    public Calculation calculate(List<Weight> sourceWeights, List<Workout> sourceWorkouts) {
        Map<Series, List<Observation>> observations = new LinkedHashMap<>();
        addBodyObservations(observations, sourceWeights);
        addWorkoutObservations(observations, sourceWorkouts);

        List<CurrentRecord> current = new ArrayList<>();
        List<HistoryEvent> history = new ArrayList<>();
        for (var entry : observations.entrySet()) {
            Series series = entry.getKey();
            BigDecimal best = null;
            Observation currentSource = null;
            List<HistoryEventDraft> drafts = new ArrayList<>();
            for (Observation observation : entry.getValue()) {
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
        current.sort(currentComparator());
        history.sort(historyComparator());
        return new Calculation(List.copyOf(current), List.copyOf(history));
    }

    private void addBodyObservations(Map<Series, List<Observation>> observations, List<Weight> sourceWeights) {
        List<Weight> weights = new ArrayList<>(sourceWeights);
        weights.sort(Comparator.comparing(Weight::getMeasuredAt).thenComparing(Weight::getId));
        for (Weight weight : weights) {
            LocalDate date = DateTimes.toLocalDate(weight.getMeasuredAt());
            Source source = new Source(PersonalRecordSourceType.WEIGHT, weight.getId(), null, null);
            add(observations, new Series(PersonalRecordMetric.BODY_WEIGHT, null, null), weight.getWeight(), date, source);
            add(observations, new Series(PersonalRecordMetric.BODY_FAT_MASS, null, null), weight.getFat(), date, source);
            add(observations, new Series(PersonalRecordMetric.BODY_FAT_PERCENTAGE, null, null), weight.getFatPercentage(), date, source);
            add(observations, new Series(PersonalRecordMetric.BODY_MUSCLE_MASS, null, null), weight.getMuscle(), date, source);
            add(observations, new Series(PersonalRecordMetric.BODY_MUSCLE_PERCENTAGE, null, null), weight.getMusclePercentage(), date, source);
        }
    }

    private void addWorkoutObservations(Map<Series, List<Observation>> observations, List<Workout> sourceWorkouts) {
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

    private void addWorkoutSegment(Map<Series, List<Observation>> observations, Exercise exercise, WorkoutSegment segment, LocalDate date, Source source) {
        switch (exercise.getTrackingMode()) {
            case REPS -> {
                BigDecimal load = normalizedLoad(segment.getWeight());
                add(observations, new Series(PersonalRecordMetric.WORKOUT_HEAVIEST_LOAD, exercise, null), load, date, source);
                add(observations, new Series(PersonalRecordMetric.WORKOUT_REPETITIONS, exercise, load), BigDecimal.valueOf(segment.getRepetitions()), date, source);
            }
            case SECONDS -> {
                BigDecimal load = normalizedLoad(segment.getWeight());
                add(observations, new Series(PersonalRecordMetric.WORKOUT_HEAVIEST_LOAD, exercise, null), load, date, source);
                add(observations, new Series(PersonalRecordMetric.WORKOUT_DURATION, exercise, load), BigDecimal.valueOf(segment.getDurationSeconds()), date, source);
            }
            case CARDIO -> {
                add(observations, new Series(PersonalRecordMetric.CARDIO_DURATION, exercise, null), BigDecimal.valueOf(segment.getDurationSeconds()), date, source);
                addOptional(observations, new Series(PersonalRecordMetric.CARDIO_SPEED, exercise, null), segment.getSpeedKph(), date, source);
                addOptional(observations, new Series(PersonalRecordMetric.CARDIO_DISTANCE, exercise, null), segment.getDistanceKm(), date, source);
                addOptional(observations, new Series(PersonalRecordMetric.CARDIO_INCLINE, exercise, null), segment.getInclinePercent(), date, source);
                addOptional(observations, new Series(PersonalRecordMetric.CARDIO_RESISTANCE, exercise, null), segment.getResistanceLevel() == null ? null : BigDecimal.valueOf(segment.getResistanceLevel()), date, source);
            }
        }
    }

    private void addOptional(Map<Series, List<Observation>> observations, Series series, BigDecimal value, LocalDate date, Source source) {
        if (value != null) {
            add(observations, series, value, date, source);
        }
    }

    private void add(Map<Series, List<Observation>> observations, Series series, BigDecimal value, LocalDate date, Source source) {
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
            .thenComparing(event -> event.source().id(), Comparator.reverseOrder())
            .thenComparing(event -> event.source().linePosition(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(event -> event.source().segmentPosition(), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(event -> event.series().metric());
    }

    public record Calculation(List<CurrentRecord> current, List<HistoryEvent> history) {
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
