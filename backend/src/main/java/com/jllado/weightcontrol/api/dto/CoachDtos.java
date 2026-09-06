package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.BloodPressureData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.CoachingPlanData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.HabitData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.MoodData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.RecentReflectionData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.SicknessData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.SleepData;
import com.jllado.weightcontrol.api.dto.HealthDataContextDtos.WorkoutExerciseData;
import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.ProgressPhotoSetResponse;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.HealthConstraintSource;
import com.jllado.weightcontrol.domain.HealthConstraintType;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.MealDish;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.PersonalRecordDirection;
import com.jllado.weightcontrol.domain.PersonalRecordDomain;
import com.jllado.weightcontrol.domain.PersonalRecordEventKind;
import com.jllado.weightcontrol.domain.PersonalRecordMetric;
import com.jllado.weightcontrol.domain.PersonalRecordUnit;
import jakarta.validation.constraints.AssertTrue;
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
        boolean requestedRangeIsInclusive,
        boolean absentBackPainEpisodesMeanNoProblem
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
        BigDecimal muscleChangeKg,
        PerformanceWeekData performanceWeek
    ) {
    }

    public record PerformanceWeekData(LocalDate startDate, LocalDate endDate, BigDecimal routineCompletionPercentage) {
    }

    public record VitalsContext(List<BloodPressureData> bloodPressures, List<LipidPanelData> lipidPanels) {
    }

    public record LipidPanelData(
        LocalDate date,
        Integer totalCholesterol,
        Integer hdlCholesterol,
        Integer ldlCholesterol,
        Integer triglycerides
    ) {
    }

    public record NutritionContext(
        List<NutritionDailyTotalData> dailyTotals,
        List<NutritionMealData> meals,
        List<NutritionFastingPeriodData> fastingPeriods
    ) {
    }

    public record NutritionDailyTotalData(
        LocalDate date,
        int calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        boolean macrosComplete
    ) {
    }

    public record NutritionMealData(
        LocalDate date,
        MealType mealType,
        int mealSequence,
        LocalTime mealTime,
        int calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydrateGrams,
        BigDecimal fatGrams,
        String notes,
        MealSource source,
        List<NutritionDishData> dishes,
        Integer durationMinutes
    ) {
    }

    public record NutritionDishData(String name, int calories, BigDecimal proteinGrams, BigDecimal carbohydrateGrams, BigDecimal fatGrams) {
        public static NutritionDishData from(MealDish dish) {
            return new NutritionDishData(dish.getName(), dish.getCalories(), dish.getProteinGrams(), dish.getCarbohydrateGrams(), dish.getFatGrams());
        }
    }

    public record NutritionFastingPeriodData(
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String notes,
        com.jllado.weightcontrol.domain.FastingPeriodSource source
    ) {
    }

    public record TrainingContext(List<CoachWorkoutData> days, List<WorkoutExerciseData> exerciseSummaries) {
    }

    public record CoachWorkoutData(
        LocalDate date,
        String note,
        List<String> exercises,
        List<String> warmUps,
        Integer totalDurationSeconds,
        BigDecimal totalDistanceKm,
        Integer totalCalories,
        BigDecimal strengthVolumeKg,
        WorkoutAssessmentSummary assessment
    ) {
    }

    public record WorkoutAssessmentSummary(
        int goalAlignmentScore,
        int estimatedTrainingDemandScore,
        String rationale,
        String strength,
        String improvement,
        String nextWorkoutAction,
        String goalSnapshot
    ) {
    }

    public record ConfirmedRequest(@AssertTrue boolean confirmed) {
    }

    public enum HealthEntryType {
        WEIGHT,
        BLOOD_PRESSURE,
        MOOD,
        SLEEP,
        BACK_PAIN_EPISODE,
        SICKNESS,
        LIPID_PANEL
    }

    public record CoachHealthEntryResponse(HealthEntryType type, Object entry) {
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

    public record HealthConstraintsContext(List<HealthConstraintData> constraints) {
    }

    public record ActivePlanContext(CoachingPlanData plan) {
    }

    public record HealthConstraintData(
        HealthConstraintType type,
        String title,
        String details,
        HealthConstraintSource source,
        LocalDate startDate,
        LocalDate endDate
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

    public record RecordsContext(
        List<CoachRecordData> current,
        List<CoachRecordEventData> progression,
        int page,
        int pageSize,
        long currentTotal,
        long progressionTotal,
        boolean hasMore
    ) {
    }

    public record CoachRecordData(
        PersonalRecordMetric metric,
        String metricLabel,
        PersonalRecordDomain domain,
        PersonalRecordDirection direction,
        BigDecimal value,
        PersonalRecordUnit unit,
        LocalDate recordDate,
        String subjectType,
        String subjectLabel,
        String qualifierLabel
    ) {
    }

    public record CoachRecordEventData(
        PersonalRecordMetric metric,
        String metricLabel,
        PersonalRecordDomain domain,
        PersonalRecordDirection direction,
        PersonalRecordEventKind kind,
        BigDecimal value,
        BigDecimal previousValue,
        PersonalRecordUnit unit,
        LocalDate recordDate,
        boolean currentRecord,
        String subjectType,
        String subjectLabel,
        String qualifierLabel
    ) {
    }

    public record DecisionData(LocalDate date, DecisionOutcomeType outcome) {
    }

    public record DecisionRangeSummary(long wins, long misses, BigDecimal winRate, int endingWinStreak) {
    }

    public record ReflectionsContext(List<RecentReflectionData> reflections) {
    }

    public record ProgressPhotosContext(List<ProgressPhotoSetResponse> photoSets) {
    }
}
