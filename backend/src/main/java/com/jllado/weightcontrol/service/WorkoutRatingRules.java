package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentPlannedSessionData;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentWeeklyPlanData;
import com.jllado.weightcontrol.domain.WorkoutPlanSessionEvidence;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.WorkoutPlanDay;
import com.jllado.weightcontrol.domain.WorkoutPlanTargetSnapshot;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/** Rules for a single training day, shared by app and Coach saves. */
public final class WorkoutRatingRules {
    public static final String DESCRIPTION = """
        Alignment: use the dated weekly plan, falling back to targets stored on recordings. Match planned sessions once by snapshot key, then uniquely by the same exercises and sauna flag for manual recordings. Match exercises by catalog identity when available, otherwise by saved name. For each planned set or round in order, take the lowest recorded/planned ratio among positive target fields, capped at 100%. A missing or skipped set earns zero. Extra work earns no extra credit. A session scores 1 + 9 × mean completion. An unmatched session or missing targets scores a neutral 5. Average session scores and round half up. A planned rest day scores 10 without completed training, 3 with training.
        Demand: count completed TRAINING strength sets plus cardio seconds divided by 300. Zero units scores 1. Otherwise start at 2 + floor(units / 3), capped at 9. Ignore warm-ups, stretching, sauna, skips, rest and overall session time. For the latest three matching days in the previous 90 days, compare daily totals per shared exercise and loading basis: reps or seconds, multiplied by recorded positive kg on loaded sets. Average current/prior ratios equally across shared exercises and then days. At or below 75% subtract 1; at or above 125% add 1. No comparable evidence means no adjustment. Clamp to 1–10.""";

    private WorkoutRatingRules() {}

    record Rating(int alignment, int demand, String rationale, String strength, String improvement, String action, String goal) {}
    private record Alignment(int score, String goal, String evidence, String action) {}
    private record LoadKey(Long exerciseId, boolean weighted) {}
    record Load(long strengthSets, long cardioSeconds, Map<LoadKey, BigInteger> totals) {
        Set<Long> exerciseIds(boolean weighted) {
            return totals.keySet().stream().filter(key -> key.weighted() == weighted).map(LoadKey::exerciseId).collect(Collectors.toSet());
        }
    }

    static Rating calculate(List<Workout> sessions, AssessmentWeeklyPlanData weekly, Load current, List<List<Workout>> history) {
        double units = current.strengthSets() + current.cardioSeconds() / 300.0;
        var alignment = alignment(sessions, weekly, units > 0);
        var comparisons = history.stream().map(WorkoutRatingRules::load)
            .map(prior -> current.totals().entrySet().stream().filter(entry -> prior.totals().containsKey(entry.getKey()))
                .map(entry -> new WorkoutLoadRatio(entry.getValue(), prior.totals().get(entry.getKey()))).toList())
            .filter(ratios -> !ratios.isEmpty()).map(WorkoutLoadRatio::mean).toList();
        int base = units == 0 ? 1 : (int) Math.min(9, 2 + Math.floor(units / 3));
        var ratio = comparisons.isEmpty() ? null : WorkoutLoadRatio.mean(comparisons);
        int adjustment = ratio == null ? 0 : ratio.adjustment();
        String comparison = ratio == null ? "No comparable prior workload."
            : "%s of comparable workload across %d prior day(s).".formatted(ratio.percentage(), comparisons.size());
        return new Rating(alignment.score(), Math.clamp(base + adjustment, 1, 10), alignment.evidence(),
            "%d completed strength set(s), %d recorded cardio second(s).".formatted(current.strengthSets(), current.cardioSeconds()),
            comparison, alignment.action(), alignment.goal());
    }

    private static Alignment alignment(List<Workout> sessions, AssessmentWeeklyPlanData weekly, boolean trained) {
        if (weekly.day() != null && weekly.day().rest()) {
            return new Alignment(trained ? 3 : 10, "Dated weekly rest day", trained
                ? "Training was recorded on a planned rest day." : "The planned rest day had no recorded training load.",
                "Check the next dated plan before training.");
        }
        var expected = weekly.day() == null ? List.<AssessmentPlannedSessionData>of() : weekly.day().sessions();
        var scores = new ArrayList<Double>();
        int matched = 0;
        if (weekly.day() != null) {
            var remaining = new ArrayList<>(sessions);
            var unmatched = new ArrayList<AssessmentPlannedSessionData>();
            for (var plan : expected) {
                var recording = remaining.stream().filter(workout -> plan.sessionKey().equals(snapshotKey(workout))).findFirst();
                if (recording.isEmpty()) unmatched.add(plan);
                else {
                    remaining.remove(recording.get());
                    scores.add(sessionScore(recording.get(), plan.plannedTargets(), plan.saunaRoundsMinutes()));
                    matched++;
                }
            }
            for (var plan : unmatched) {
                var candidates = remaining.stream().filter(workout -> snapshotKey(workout) == null && manualMatch(workout, plan)).toList();
                if (candidates.size() == 1 && unmatched.stream().filter(other -> manualMatch(candidates.getFirst(), other)).count() == 1) {
                    scores.add(sessionScore(candidates.getFirst(), plan.plannedTargets(), plan.saunaRoundsMinutes()));
                    matched++;
                } else scores.add(5.0);
            }
        } else {
            for (var workout : sessions) {
                if (snapshotKey(workout) == null) continue;
                scores.add(sessionScore(workout, targets(workout), plannedRounds(workout)));
                matched++;
            }
        }
        if (scores.isEmpty()) return new Alignment(5, "No dated workout targets", "No dated targets were available. Alignment is neutral.",
            "Save plan targets to compare future sessions.");
        double total = scores.stream().mapToDouble(Double::doubleValue).sum();
        String source = weekly.day() == null ? "Recorded session targets" : "Dated weekly workout targets";
        int unmatched = scores.size() - matched;
        String evidence = matched == 0 ? "No planned session could be matched confidently. Alignment is neutral."
            : "Matched %d of %d planned sessions with %d%% average target completion.".formatted(matched, scores.size(),
                Math.round(100 * ((total - 5 * unmatched) / matched - 1) / 9));
        return new Alignment((int) Math.round(total / scores.size()), source, evidence,
            matched < scores.size() ? "Check which planned sessions were recorded." : "Review the recorded sets against the next session’s targets.");
    }

    private static String snapshotKey(Workout workout) {
        if (targets(workout).isEmpty() && plannedRounds(workout).isEmpty()) return null;
        return WorkoutPlanSessionEvidence.key(workout.getPlannedSessionName(), targets(workout), plannedRounds(workout));
    }

    private static List<WorkoutPlanTargetSnapshot> targets(Workout workout) {
        return workout.getPlannedTargets() == null ? List.of() : workout.getPlannedTargets();
    }

    private static List<Integer> plannedRounds(Workout workout) {
        return workout.getPlannedSaunaRoundsMinutes() == null ? List.of() : workout.getPlannedSaunaRoundsMinutes();
    }

    private static boolean manualMatch(Workout workout, AssessmentPlannedSessionData plan) {
        if (workout.isSaunaSession() != plan.saunaSession()) return false;
        return workout.getLines().size() == plan.plannedTargets().size() && plan.plannedTargets().stream()
            .allMatch(target -> workout.getLines().stream().anyMatch(line -> matches(line.getExercise(), target)));
    }

    private static boolean matches(Exercise exercise, WorkoutPlanTargetSnapshot target) {
        boolean sameExercise = target.exerciseId() != null ? target.exerciseId().equals(exercise.getId())
            : target.exerciseName().equals(exercise.getName());
        return sameExercise && exercise.getTrackingMode() == target.trackingMode() && exercise.getExerciseType() == target.exerciseType();
    }

    private static double sessionScore(Workout workout, List<WorkoutPlanTargetSnapshot> targets, List<Integer> rounds) {
        var credits = new ArrayList<Double>();
        for (var target : targets) {
            var segments = workout.getLines().stream().filter(line -> matches(line.getExercise(), target))
                .findFirst().map(line -> line.getSegments()).orElse(List.of());
            for (int i = 0; i < target.segments().size(); i++) {
                credits.add(i >= segments.size() || segments.get(i).isSkipped() ? 0.0 : completion(segments.get(i), target.segments().get(i)));
            }
        }
        for (int i = 0; i < rounds.size(); i++) {
            var actual = workout.getSaunaRoundsMinutes();
            credits.add(actual == null || i >= actual.size() ? 0.0 : fraction(actual.get(i), rounds.get(i)));
        }
        return credits.isEmpty() ? 5 : 1 + 9 * credits.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static double completion(WorkoutSegment actual, WorkoutPlanDay.Segment target) {
        return DoubleStream.of(fraction(actual.getRepetitions(), target.repetitions()), fraction(actual.getDurationSeconds(), target.durationSeconds()),
            fraction(actual.getBreaths(), target.breaths()), fraction(actual.getWeight(), target.weight()), fraction(actual.getSpeedKph(), target.speedKph()),
            fraction(actual.getCadenceRpm(), target.cadenceRpm()), fraction(actual.getDistanceKm(), target.distanceKm()),
            fraction(actual.getInclinePercent(), target.inclinePercent()), fraction(actual.getResistanceLevel(), target.resistanceLevel())).min().orElseThrow();
    }

    private static double fraction(Number actual, Number target) {
        if (target == null || target.doubleValue() == 0) return 1;
        return actual == null ? 0 : Math.min(1, actual.doubleValue() / target.doubleValue());
    }

    static Load load(List<Workout> sessions) {
        long sets = 0;
        long seconds = 0;
        var totals = new HashMap<LoadKey, BigInteger>();
        for (var workout : sessions) {
            for (var line : workout.getLines()) {
                var exercise = line.getExercise();
                if (exercise.getExerciseType() != ExerciseType.TRAINING) continue;
                for (var segment : line.getSegments()) {
                    if (segment.isSkipped()) continue;
                    if (exercise.getTrackingMode() == ExerciseTrackingMode.CARDIO) seconds += segment.getDurationSeconds();
                    else sets++;
                    long quantity = exercise.getTrackingMode() == ExerciseTrackingMode.REPS ? segment.getRepetitions() : segment.getDurationSeconds();
                    boolean weighted = segment.getWeight() != null && segment.getWeight().signum() > 0;
                    var amount = BigInteger.valueOf(quantity);
                    if (weighted) amount = amount.multiply(segment.getWeight().movePointRight(2).toBigIntegerExact());
                    totals.merge(new LoadKey(exercise.getId(), weighted), amount, BigInteger::add);
                }
            }
        }
        return new Load(sets, seconds, totals);
    }
}
