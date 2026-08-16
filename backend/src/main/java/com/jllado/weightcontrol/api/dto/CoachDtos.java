package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.BloodPressureData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.CalorieData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.HabitData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.MoodData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.RecentReflectionData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.SicknessData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.SleepData;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.MoodPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class CoachDtos {

    private CoachDtos() {
    }

    public record CoachCatalogResponse(
        String timezone,
        OffsetDateTime currentLocalDateTime,
        LocalDate lastCompletedDate,
        List<DomainAvailability> domains
    ) {
    }

    public record DomainAvailability(CoachDomain domain, long recordCount, LocalDate firstDate, LocalDate lastDate) {
    }

    public record CoachContextResponse(
        String timezone,
        OffsetDateTime currentLocalDateTime,
        LocalDate from,
        LocalDate to,
        LocalDate lastCompletedDate,
        boolean endDateComplete,
        CoachDataSemantics dataSemantics,
        Map<CoachDomain, Object> data
    ) {
    }

    public record CoachDataSemantics(
        boolean absentRecordsAreUnknown,
        boolean recordedZeroCaloriesAreValid,
        boolean requestedRangeIsInclusive
    ) {
    }

    public record BodyContext(List<BodyMeasurementData> measurements) {
    }

    public record BodyMeasurementData(
        LocalDate date,
        BigDecimal weightKg,
        BigDecimal fatPercentage,
        BigDecimal fatMassKg,
        BigDecimal muscleMassKg,
        BigDecimal musclePercentage,
        BigDecimal weightChangeKg,
        BigDecimal fatChangeKg,
        BigDecimal muscleChangeKg
    ) {
    }

    public record VitalsContext(List<BloodPressureData> bloodPressures) {
    }

    public record NutritionContext(List<CalorieData> dailyTotals) {
    }

    public record RecoveryContext(List<MoodData> moods, List<SleepData> sleeps) {
    }

    public record BehaviorContext(
        List<CoachDailyStatusData> dailyStatuses,
        List<HabitData> habits,
        List<CoachRoutineData> routines
    ) {
    }

    public record CoachDailyStatusData(
        LocalDate date,
        boolean complete,
        BigDecimal routinesPercentage,
        BigDecimal weightPercentage,
        BigDecimal bloodPressurePercentage,
        BigDecimal flexibilityPercentage,
        BigDecimal mindPercentage,
        BigDecimal routinesStatus,
        BigDecimal weightStatus,
        BigDecimal bloodPressureStatus,
        BigDecimal flexibilityStatus,
        BigDecimal mindStatus
    ) {
    }

    public record CoachRoutineData(
        String name,
        LocalDate startDate,
        List<String> types,
        List<LocalDate> checkinDates
    ) {
    }

    public record HealthEventsContext(
        List<SicknessData> sicknesses,
        List<BackPainEpisodeData> backPainEpisodes
    ) {
    }

    public record BackPainEpisodeData(
        LocalDate date,
        LocalTime time,
        MoodPeriod period,
        BackRegion region,
        BackSide side,
        BackPainSeverity severity,
        String note
    ) {
    }

    public record DecisionsContext(List<DecisionData> outcomes, DecisionRangeSummary summary) {
    }

    public record DecisionData(LocalDate date, DecisionOutcomeType outcome) {
    }

    public record DecisionRangeSummary(long wins, long misses, BigDecimal winRate, int endingWinStreak) {
    }

    public record ReflectionsContext(List<RecentReflectionData> reflections) {
    }
}
