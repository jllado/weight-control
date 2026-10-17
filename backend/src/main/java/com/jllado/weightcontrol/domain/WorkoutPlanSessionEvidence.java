package com.jllado.weightcontrol.domain;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.util.DigestUtils;

/** Canonical snapshot identity, independent of catalog IDs, descriptions and decimal scale. */
public final class WorkoutPlanSessionEvidence {
    private WorkoutPlanSessionEvidence() {}

    public static String key(String name, List<WorkoutPlanTargetSnapshot> targets, List<Integer> saunaRounds) {
        var lines = targets.stream().map(target -> {
            var group = target.supersetGroupId() == null ? "" : targets.stream()
                .filter(other -> Objects.equals(target.supersetGroupId(), other.supersetGroupId()))
                .map(WorkoutPlanSessionEvidence::targetSignature).sorted().map(WorkoutPlanSessionEvidence::field).collect(Collectors.joining());
            return field(targetSignature(target)) + field(group);
        }).sorted().map(WorkoutPlanSessionEvidence::field).collect(Collectors.joining());
        return DigestUtils.md5DigestAsHex((field(name) + field(lines) + field(saunaRounds.toString())).getBytes(StandardCharsets.UTF_8));
    }

    public static WorkoutPlanTargetSnapshot snapshot(WorkoutPlanDay.Target target) {
        return new WorkoutPlanTargetSnapshot(target.exerciseName(), target.exerciseDescription(), target.trackingMode(),
            target.exerciseType(), target.cardioMetric(), target.stretchingUnit(), target.segments(), target.supersetGroupId());
    }

    private static String targetSignature(WorkoutPlanTargetSnapshot target) {
        return field(target.exerciseName()) + field(target.trackingMode()) + field(target.exerciseType())
            + field(target.cardioMetric()) + field(target.stretchingUnit()) + field(target.segments().stream().map(segment ->
                field(segment.repetitions()) + field(segment.durationSeconds()) + field(segment.weight()) + field(segment.speedKph())
                + field(segment.cadenceRpm()) + field(segment.distanceKm()) + field(segment.inclinePercent())
                + field(segment.resistanceLevel()) + field(segment.breaths())).sorted().map(WorkoutPlanSessionEvidence::field).collect(Collectors.joining()));
    }

    private static String field(Object value) {
        if (value == null) return "-1:";
        String text = value instanceof BigDecimal number ? number.stripTrailingZeros().toPlainString() : value.toString();
        return text.length() + ":" + text;
    }
}
