package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentPlannedSessionData;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.WorkoutPlanRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

@Service
public class WeeklyWorkoutTargetService {
    private final WorkoutPlanRepository plans;
    public WeeklyWorkoutTargetService(WorkoutPlanRepository plans) { this.plans = plans; }
    public WeeklyWorkoutTargets capture(User user, LocalDate friday) {
        return new WeeklyWorkoutTargets(period(user, friday), period(user, friday.minusWeeks(1)), period(user, friday.minusWeeks(52)));
    }
    private WeeklyWorkoutTargets.Period period(User user, LocalDate end) {
        var days = end.minusDays(6).datesUntil(end.plusDays(1)).map(date -> new WeeklyWorkoutTargets.Day(date,
            WorkoutPlanEvidenceResolver.resolve(date, plans.findAssessmentCandidates(user, date,
                date.atStartOfDay(DateTimes.USER_ZONE).toInstant(), date.plusDays(1).atStartOfDay(DateTimes.USER_ZONE).toInstant())))).toList();
        return summarize(days);
    }
    static WeeklyWorkoutTargets.Period summarize(List<WeeklyWorkoutTargets.Day> days) {
        if (days.stream().anyMatch(day -> !day.evidence().evidenceStatus().equals("AVAILABLE")))
            return new WeeklyWorkoutTargets.Period(days, null, null, null, null, null, null);
        var sessions = days.stream().flatMap(day -> day.evidence().day().sessions().stream()).toList();
        var cardio = sessions.stream().flatMap(session -> session.plannedTargets().stream()).filter(WeeklyWorkoutTargetService::cardio).toList();
        var segments = cardio.stream().flatMap(target -> target.segments().stream()).toList();
        Integer seconds = segments.stream().anyMatch(segment -> segment.durationSeconds() == null) ? null
            : segments.stream().mapToInt(WorkoutPlanDay.Segment::durationSeconds).sum();
        return new WeeklyWorkoutTargets.Period(days, sessions.size(), count(sessions, target -> target.exerciseType() == ExerciseType.TRAINING && target.trackingMode() != ExerciseTrackingMode.CARDIO),
            count(sessions, WeeklyWorkoutTargetService::cardio), count(sessions, target -> target.exerciseType() == ExerciseType.STRETCHING),
            (int) sessions.stream().filter(AssessmentPlannedSessionData::saunaSession).count(), seconds);
    }
    private static boolean cardio(WorkoutPlanTargetSnapshot target) {
        return target.exerciseType() == ExerciseType.TRAINING && target.trackingMode() == ExerciseTrackingMode.CARDIO;
    }
    private static int count(List<AssessmentPlannedSessionData> sessions, Predicate<WorkoutPlanTargetSnapshot> predicate) {
        return (int) sessions.stream().filter(session -> session.plannedTargets().stream().anyMatch(predicate)).count();
    }
}
