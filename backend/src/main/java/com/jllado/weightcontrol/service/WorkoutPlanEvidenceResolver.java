package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.*;
import com.jllado.weightcontrol.domain.WorkoutPlan;
import com.jllado.weightcontrol.domain.WorkoutPlanSessionEvidence;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.util.List;

/** The same dated, conservative plan evidence used by assessments and frozen weekly reports. */
public final class WorkoutPlanEvidenceResolver {
    private WorkoutPlanEvidenceResolver() { }
    public static AssessmentWeeklyPlanData resolve(LocalDate date, List<WorkoutPlan> plans) {
        if (plans.isEmpty()) return new AssessmentWeeklyPlanData("UNAVAILABLE", null, null, null, null);
        if (plans.size() != 1) return new AssessmentWeeklyPlanData("AMBIGUOUS", null, null, null, null);
        var plan = plans.getFirst();
        var dayStart = date.atStartOfDay(DateTimes.USER_ZONE).toInstant();
        var dayEnd = date.plusDays(1).atStartOfDay(DateTimes.USER_ZONE).toInstant();
        if (!plan.getUpdatedAt().isBefore(dayEnd))
            return new AssessmentWeeklyPlanData("HISTORICAL_VERSION_UNAVAILABLE", plan.getStartDate(), plan.getReviewDate(), plan.getUpdatedAt(), null);
        if (plan.getUpdatedAt().isAfter(dayStart) || (plan.getArchivedAt() != null && plan.getArchivedAt().isBefore(dayEnd)))
            return new AssessmentWeeklyPlanData("AMBIGUOUS", plan.getStartDate(), plan.getReviewDate(), plan.getUpdatedAt(), null);
        var day = plan.getDays().stream().filter(candidate -> candidate.day() == date.getDayOfWeek()).findFirst().orElseThrow();
        var sessions = day.sessions().stream().map(session -> {
            var targets = session.lines().stream().map(WorkoutPlanSessionEvidence::snapshot).toList();
            return new AssessmentPlannedSessionData(session.name(), session.note(), WorkoutPlanSessionEvidence.key(session.name(), targets, session.saunaRoundsMinutes()),
                targets, session.saunaSession(), session.saunaRoundsMinutes());
        }).toList();
        return new AssessmentWeeklyPlanData("AVAILABLE", plan.getStartDate(), plan.getReviewDate(), plan.getUpdatedAt(),
            new AssessmentPlannedDayData(day.day(), day.rest(), day.note(), sessions));
    }

}
