package com.jllado.weightcontrol.domain;

import java.util.List;

public record WorkoutPlanTargetSnapshot(String exerciseName, String exerciseDescription, ExerciseTrackingMode trackingMode,
    ExerciseType exerciseType, CardioMetric cardioMetric, StretchingUnit stretchingUnit,
    List<WorkoutPlanDay.Segment> segments) { }
