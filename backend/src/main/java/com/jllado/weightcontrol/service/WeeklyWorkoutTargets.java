package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentWeeklyPlanData;
import java.time.LocalDate;
import java.util.List;

public record WeeklyWorkoutTargets(Period current, Period previous, Period yearAgo) {
    public record Day(LocalDate date, AssessmentWeeklyPlanData evidence) { }
    public record Period(List<Day> days, Integer totalSessions, Integer strengthSessions, Integer cardioSessions,
        Integer stretchingSessions, Integer saunaSessions, Integer activeCardioSeconds) { }
}
