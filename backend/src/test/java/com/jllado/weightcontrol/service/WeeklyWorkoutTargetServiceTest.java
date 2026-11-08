package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.*;
import com.jllado.weightcontrol.domain.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyWorkoutTargetServiceTest {
    @Test void mixedSessionsCountIndependentlyAndOnlyTrainingCardioContributesDuration() {
        var mixed = new AssessmentPlannedSessionData("Mixed", null, "key", List.of(
            target(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, null),
            target(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, 120),
            target(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, 60),
            target(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, 30),
            target(ExerciseType.WARM_UP, ExerciseTrackingMode.CARDIO, 600)), true, List.of(10));
        var period = WeeklyWorkoutTargetService.summarize(List.of(day("AVAILABLE", List.of(mixed))));
        assertEquals(1, period.totalSessions()); assertEquals(1, period.strengthSessions());
        assertEquals(1, period.cardioSessions()); assertEquals(1, period.stretchingSessions());
        assertEquals(1, period.saunaSessions()); assertEquals(180, period.activeCardioSeconds());
    }
    @Test void unavailableHistoricalDayDoesNotInventZeroTargets() {
        var period = WeeklyWorkoutTargetService.summarize(List.of(day("HISTORICAL_VERSION_UNAVAILABLE", List.of())));
        assertNull(period.strengthSessions()); assertNull(period.activeCardioSeconds());
    }
    @Test void missingCardioDurationDoesNotHideKnownSessionTargets() {
        var session = new AssessmentPlannedSessionData("Ride", null, "key", List.of(target(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, null)), false, List.of());
        var period = WeeklyWorkoutTargetService.summarize(List.of(day("AVAILABLE", List.of(session))));
        assertEquals(1, period.cardioSessions()); assertNull(period.activeCardioSeconds());
    }
    @Test void resolverRetainsHistoricalAmbiguityAndDoesNotReadTodaysReplacement() {
        var date = LocalDate.of(2026, 10, 19); var plan = new WorkoutPlan();
        plan.setStartDate(date); plan.setReviewDate(date.plusDays(6));
        plan.setUpdatedAt(date.plusDays(1).atStartOfDay(com.jllado.weightcontrol.util.DateTimes.USER_ZONE).toInstant());
        assertEquals("HISTORICAL_VERSION_UNAVAILABLE", WorkoutPlanEvidenceResolver.resolve(date, List.of(plan)).evidenceStatus());
        plan.setUpdatedAt(date.atTime(12, 0).atZone(com.jllado.weightcontrol.util.DateTimes.USER_ZONE).toInstant());
        assertEquals("AMBIGUOUS", WorkoutPlanEvidenceResolver.resolve(date, List.of(plan)).evidenceStatus());
        assertEquals("AMBIGUOUS", WorkoutPlanEvidenceResolver.resolve(date, List.of(plan, plan)).evidenceStatus());
    }
    private WeeklyWorkoutTargets.Day day(String status, List<AssessmentPlannedSessionData> sessions) {
        return new WeeklyWorkoutTargets.Day(LocalDate.of(2026, 10, 19), new AssessmentWeeklyPlanData(status, null, null, null,
            status.equals("AVAILABLE") ? new AssessmentPlannedDayData(DayOfWeek.MONDAY, false, null, sessions) : null));
    }
    private WorkoutPlanTargetSnapshot target(ExerciseType type, ExerciseTrackingMode mode, Integer seconds) {
        return new WorkoutPlanTargetSnapshot("Exercise", "", mode, type, null, StretchingUnit.SECONDS,
            List.of(new WorkoutPlanDay.Segment(null, seconds, null, null, null, null, null, null, null)));
    }
}
