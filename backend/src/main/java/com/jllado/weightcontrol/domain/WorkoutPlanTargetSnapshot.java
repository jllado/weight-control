package com.jllado.weightcontrol.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record WorkoutPlanTargetSnapshot(String exerciseName, String exerciseDescription, ExerciseTrackingMode trackingMode,
    ExerciseType exerciseType, CardioMetric cardioMetric, StretchingUnit stretchingUnit,
    List<WorkoutPlanDay.Segment> segments, String supersetGroupId, @JsonIgnore Long exerciseId) {
    public WorkoutPlanTargetSnapshot(String exerciseName, String exerciseDescription, ExerciseTrackingMode trackingMode,
        ExerciseType exerciseType, CardioMetric cardioMetric, StretchingUnit stretchingUnit, List<WorkoutPlanDay.Segment> segments, String supersetGroupId) {
        this(exerciseName, exerciseDescription, trackingMode, exerciseType, cardioMetric, stretchingUnit, segments, supersetGroupId, null);
    }
    public WorkoutPlanTargetSnapshot(String exerciseName, String exerciseDescription, ExerciseTrackingMode trackingMode, ExerciseType exerciseType, CardioMetric cardioMetric, StretchingUnit stretchingUnit, List<WorkoutPlanDay.Segment> segments) { this(exerciseName, exerciseDescription, trackingMode, exerciseType, cardioMetric, stretchingUnit, segments, null); }
}
