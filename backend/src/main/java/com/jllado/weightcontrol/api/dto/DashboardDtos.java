package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record DashboardResponse(
        LocalDate anchorDate,
        DailyStatusResponse dailyStatus,
        DailyStatusResponse lastWeekDailyStatus,
        WeekStatusResponse weekStatus,
        WeekStatusResponse weekAgoStatus
    ) {
    }

    public record DailyStatusResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        WeightSummary weight,
        BloodPressureSummary bloodPressure,
        Integer totalRoutines,
        Integer totalWeightRoutines,
        Integer totalBloodPressureRoutines,
        Integer totalFlexibilityRoutines,
        Integer totalMindRoutines,
        Integer routinesDone,
        Integer weightDone,
        Integer bloodPressureDone,
        Integer flexibilityDone,
        Integer mindDone,
        MoodSummary mood,
        BigDecimal routinesPercentage,
        BigDecimal weightPercentage,
        BigDecimal bloodPressurePercentage,
        BigDecimal flexibilityPercentage,
        BigDecimal mindPercentage,
        BigDecimal moodTrend,
        BigDecimal routinesScore,
        BigDecimal weightScore,
        BigDecimal bloodPressureScore,
        BigDecimal flexibilityScore,
        BigDecimal mindScore,
        BigDecimal routinesStatus,
        BigDecimal weightStatus,
        BigDecimal bloodPressureStatus,
        BigDecimal flexibilityStatus,
        BigDecimal mindStatus,
        Boolean routinesCompleted
    ) {
        public static DailyStatusResponse from(DailyStatus status, Mood mood, BigDecimal moodTrend, boolean routinesCompleted) {
            return new DailyStatusResponse(
                status.getId(),
                DateTimes.formatDate(status.getStatusDate()),
                status.getStatusDate(),
                WeightSummary.from(status.getWeight()),
                BloodPressureSummary.from(status.getBloodPressure()),
                status.getTotalRoutines(),
                status.getTotalWeightRoutines(),
                status.getTotalBloodPressureRoutines(),
                status.getTotalFlexibilityRoutines(),
                status.getTotalMindRoutines(),
                status.getRoutinesDone(),
                status.getWeightDone(),
                status.getBloodPressureDone(),
                status.getFlexibilityDone(),
                status.getMindDone(),
                MoodSummary.from(mood),
                status.getRoutinesPercentage(),
                status.getWeightPercentage(),
                status.getBloodPressurePercentage(),
                status.getFlexibilityPercentage(),
                status.getMindPercentage(),
                moodTrend,
                status.getRoutinesScore(),
                status.getWeightScore(),
                status.getBloodPressureScore(),
                status.getFlexibilityScore(),
                status.getMindScore(),
                status.getRoutinesStatus(),
                status.getWeightStatus(),
                status.getBloodPressureStatus(),
                status.getFlexibilityStatus(),
                status.getMindStatus(),
                routinesCompleted
            );
        }
    }

    public record RoutinesCompletionRequest(@NotNull Boolean completed) {
    }

    public record WeekStatusResponse(
        DailyStatusResponse saturday,
        DailyStatusResponse sunday,
        DailyStatusResponse monday,
        DailyStatusResponse tuesday,
        DailyStatusResponse wednesday,
        DailyStatusResponse thursday,
        DailyStatusResponse friday,
        BigDecimal routinesPercentage,
        BigDecimal weightPercentage,
        BigDecimal bloodPressurePercentage,
        BigDecimal flexibilityPercentage,
        BigDecimal mindPercentage,
        BigDecimal moodAverage
    ) {
    }

    public record MoodSummary(Long id, String dateFormat, LocalDate date, Integer value, String note) {
        public static MoodSummary from(Mood mood) {
            return mood == null ? null : new MoodSummary(mood.getId(), DateTimes.formatDate(mood.getMoodDate()), mood.getMoodDate(), mood.getValue(), mood.getNote());
        }
    }

    public record WeightSummary(Long id, String dateFormat, BigDecimal weight, BigDecimal fatPercentage, BigDecimal musclePercentage) {
        public static WeightSummary from(Weight weight) {
            return weight == null ? null : new WeightSummary(weight.getId(), DateTimes.formatDate(weight.getMeasuredAt()), weight.getWeight(), weight.getFatPercentage(), weight.getMusclePercentage());
        }
    }

    public record BloodPressureSummary(Long id, String dateFormat, Integer upper, Integer lower) {
        public static BloodPressureSummary from(BloodPressure bloodPressure) {
            return bloodPressure == null ? null : new BloodPressureSummary(bloodPressure.getId(), DateTimes.formatDateTime(bloodPressure.getMeasuredAt()), bloodPressure.getUpper(), bloodPressure.getLower());
        }
    }
}
