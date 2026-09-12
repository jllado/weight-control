package com.jllado.weightcontrol.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;

public record WorkoutPlanDay(DayOfWeek day, boolean rest, String note, List<Target> lines) {
    public record Target(Long exerciseId, String exerciseName, String exerciseDescription, ExerciseTrackingMode trackingMode, ExerciseType exerciseType, List<Segment> segments) { }
    public record Segment(Integer repetitions, Integer durationSeconds, BigDecimal weight, BigDecimal speedKph, BigDecimal distanceKm, BigDecimal inclinePercent, Integer resistanceLevel) { }
}
