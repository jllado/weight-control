package com.jllado.weightcontrol.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record WorkoutPlanDay(DayOfWeek day, boolean rest, String note, List<Session> sessions) {
    public WorkoutPlanDay {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }
    public WorkoutPlanDay(DayOfWeek day, boolean rest, List<Session> sessions) { this(day, rest, null, sessions); }
    @JsonIgnore public List<Target> lines() { return sessions.size() == 1 ? sessions.getFirst().lines() : List.of(); }
    public record Session(String name, String note, List<Target> lines) { }
    public record Target(Long exerciseId, String exerciseName, String exerciseDescription, ExerciseTrackingMode trackingMode, ExerciseType exerciseType, CardioMetric cardioMetric, List<Segment> segments, StretchingUnit stretchingUnit) {
        public Target { if (stretchingUnit == null) stretchingUnit = StretchingUnit.SECONDS; }
    }
    public record Segment(Integer repetitions, Integer durationSeconds, BigDecimal weight, BigDecimal speedKph, BigDecimal cadenceRpm, BigDecimal distanceKm, BigDecimal inclinePercent, Integer resistanceLevel, Integer breaths) { }
}
