package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.*;
import com.jllado.weightcontrol.domain.WorkoutPlan;
import com.jllado.weightcontrol.domain.WorkoutPlanSessionEvidence;
import com.jllado.weightcontrol.domain.WorkoutPlanVersion;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The same dated, conservative plan evidence used by assessments and frozen weekly reports. */
public final class WorkoutPlanEvidenceResolver {
    private static final AssessmentWeeklyPlanData UNAVAILABLE = new AssessmentWeeklyPlanData("UNAVAILABLE", null, null, null, null);

    private WorkoutPlanEvidenceResolver() { }
    public static AssessmentWeeklyPlanData resolve(LocalDate date, List<WorkoutPlan> plans) {
        var dayStart = date.atStartOfDay(DateTimes.USER_ZONE).toInstant();
        var dayEnd = date.plusDays(1).atStartOfDay(DateTimes.USER_ZONE).toInstant();
        var candidates = plans.stream().filter(plan -> plan.getCreatedAt().isBefore(dayEnd)
                && (plan.getArchivedAt() == null || plan.getArchivedAt().isAfter(dayStart)))
            .map(plan -> evidence(date, dayStart, dayEnd, plan))
            .filter(result -> !result.evidenceStatus().equals("UNAVAILABLE")).toList();
        if (candidates.isEmpty()) return UNAVAILABLE;
        if (candidates.size() > 1) return new AssessmentWeeklyPlanData("AMBIGUOUS", null, null, null, null);
        return candidates.getFirst();
    }

    private static AssessmentWeeklyPlanData evidence(LocalDate date, Instant dayStart, Instant dayEnd, WorkoutPlan plan) {
        var versions = new ArrayList<>(plan.getPreviousVersions());
        versions.add(plan.currentVersion());
        if (versions.stream().allMatch(version -> version.getStartDate().isAfter(date))) return UNAVAILABLE;
        var first = versions.getFirst();
        if (!first.getUpdatedAt().isBefore(dayEnd)) return missing("HISTORICAL_VERSION_UNAVAILABLE", first);
        if (first.getUpdatedAt().isAfter(dayStart)) return missing("AMBIGUOUS", first);

        int index = 0;
        while (index + 1 < versions.size() && !versions.get(index + 1).getUpdatedAt().isAfter(dayStart)) index++;
        var version = versions.get(index);
        var day = version.dayOn(date);
        for (int i = index + 1; i < versions.size(); i++) {
            var next = versions.get(i);
            if (!next.getUpdatedAt().isBefore(dayEnd)) break;
            if (!Objects.equals(day, next.dayOn(date))) return missing("AMBIGUOUS", version);
        }
        if (day == null) return UNAVAILABLE;
        if (plan.getArchivedAt() != null && plan.getArchivedAt().isBefore(dayEnd)) return missing("AMBIGUOUS", version);
        var sessions = day.sessions().stream().map(session -> {
            var targets = session.lines().stream().map(WorkoutPlanSessionEvidence::snapshot).toList();
            return new AssessmentPlannedSessionData(session.name(), session.note(), WorkoutPlanSessionEvidence.key(session.name(), targets, session.saunaRoundsMinutes()),
                targets, session.saunaSession(), session.saunaRoundsMinutes());
        }).toList();
        return new AssessmentWeeklyPlanData("AVAILABLE", version.getStartDate(), version.getReviewDate(), version.getUpdatedAt(),
            new AssessmentPlannedDayData(day.day(), day.rest(), day.note(), sessions));
    }

    private static AssessmentWeeklyPlanData missing(String status, WorkoutPlanVersion version) {
        return new AssessmentWeeklyPlanData(status, version.getStartDate(), version.getReviewDate(), version.getUpdatedAt(), null);
    }
}
