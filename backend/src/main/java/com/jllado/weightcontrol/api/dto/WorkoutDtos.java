package com.jllado.weightcontrol.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.StretchingUnit;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.HistoryEventResponse;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.HistoryEventResponse;

public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    public record WorkoutPlanRequest(
        @NotNull LocalDate startDate, @NotNull LocalDate reviewDate, @Size(max = 500) String notes,
        @NotNull @Size(min = 7, max = 7) List<@NotNull @jakarta.validation.Valid WorkoutPlanDayRequest> days
    ) { }
    public record WorkoutPlanDayRequest(
        @NotNull java.time.DayOfWeek day, @NotNull Boolean rest, @Size(max = 500) String note,
        @NotNull List<@NotNull @jakarta.validation.Valid WorkoutPlanLineRequest> lines
    ) { }
    public record WorkoutPlanLineRequest(@NotNull Long exerciseId, @NotEmpty List<@NotNull @jakarta.validation.Valid WorkoutSegmentRequest> segments, StretchingUnit stretchingUnit) {
        public WorkoutPlanLineRequest { if (stretchingUnit == null) stretchingUnit = StretchingUnit.SECONDS; }
    }
    public record WorkoutPlanUpdateRequest(@NotNull @jakarta.validation.Valid WorkoutPlanRequest plan, @NotBlank String updateToken) { }
    public record CoachWorkoutPlanUpdateRequest(
        @NotNull @jakarta.validation.Valid WorkoutPlanRequest plan, @NotBlank String updateToken,
        @NotNull @jakarta.validation.constraints.AssertTrue Boolean confirmed
    ) { }
    public record WorkoutPlanResponse(Long id, LocalDate startDate, LocalDate reviewDate, String notes,
        List<com.jllado.weightcontrol.domain.WorkoutPlanDay> days, java.time.Instant createdAt,
        java.time.Instant updatedAt, java.time.Instant archivedAt, String updateToken) {
        public static WorkoutPlanResponse from(com.jllado.weightcontrol.domain.WorkoutPlan plan) {
            return new WorkoutPlanResponse(plan.getId(), plan.getStartDate(), plan.getReviewDate(), plan.getNotes(), plan.getDays(), plan.getCreatedAt(), plan.getUpdatedAt(), plan.getArchivedAt(), plan.getUpdateToken());
        }
    }
    public record WorkoutPlanSummary(Long id, LocalDate startDate, LocalDate reviewDate, java.time.Instant archivedAt) {
        public static WorkoutPlanSummary from(com.jllado.weightcontrol.domain.WorkoutPlan plan) { return new WorkoutPlanSummary(plan.getId(), plan.getStartDate(), plan.getReviewDate(), plan.getArchivedAt()); }
    }
    public record WorkoutPlanArchiveResponse(List<WorkoutPlanSummary> items, int page, long totalElements, int totalPages) { }
    public record WorkoutPlanExerciseChoice(Long id, String name, String description, ExerciseTrackingMode trackingMode, ExerciseType exerciseType) {
        public static WorkoutPlanExerciseChoice from(Exercise exercise) { return new WorkoutPlanExerciseChoice(exercise.getId(), exercise.getName(), exercise.getDescription(), exercise.getTrackingMode(), exercise.getExerciseType()); }
    }
    public record WorkoutPlanEditContext(WorkoutPlanResponse plan, List<WorkoutPlanExerciseChoice> exercises) { }

    public record ExerciseRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String description,
        @NotNull ExerciseTrackingMode trackingMode,
        @NotNull ExerciseType exerciseType
    ) {
        public ExerciseRequest(String name, String description, ExerciseTrackingMode trackingMode) {
            this(name, description, trackingMode, ExerciseType.TRAINING);
        }
    }

    public record ExerciseResponse(
        Long id,
        String name,
        String description,
        ExerciseTrackingMode trackingMode,
        ExerciseType exerciseType,
        String imageUrl,
        boolean hasCustomImage
    ) {
        public static ExerciseResponse from(Exercise exercise) {
            return new ExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getDescription(),
                exercise.getTrackingMode(),
                exercise.getExerciseType(),
                imageUrl(exercise),
                exercise.getCustomImagePath() != null
            );
        }
        private static String imageUrl(Exercise exercise) {
            String path = exercise.getCustomImagePath();
            String version = path == null ? exercise.getBuiltInImageKey() : path.substring(path.lastIndexOf('/') + 1);
            return version == null ? null : "/api/workout-exercises/" + exercise.getId() + "/image?v=" + version;
        }
    }

    public record StretchingSetRequest(
        @NotBlank @Size(max = 255) String name,
        @NotEmpty List<@NotNull @Valid StretchingSetEntryRequest> entries
    ) {}

    public record StretchingSetEntryRequest(
        @NotNull Long exerciseId,
        List<@NotNull @DecimalMin("1") Integer> durations,
        StretchingUnit stretchingUnit,
        @JsonDeserialize(contentUsing = DurationMinutesDeserializer.class) List<@NotNull @DecimalMin("1") Integer> breaths
    ) {
        public StretchingSetEntryRequest {
            if (stretchingUnit == null) stretchingUnit = StretchingUnit.SECONDS;
            if (durations == null) durations = List.of();
            if (breaths == null) breaths = List.of();
        }
        @com.fasterxml.jackson.annotation.JsonIgnore
        @jakarta.validation.constraints.AssertTrue(message = "Enter holds only in the selected unit")
        public boolean isValidHolds() { return stretchingUnit == StretchingUnit.SECONDS ? !durations.isEmpty() && breaths.isEmpty() : !breaths.isEmpty() && durations.isEmpty(); }
    }

    public record StretchingSetResponse(Long id, String name, List<StretchingSetEntryRequest> entries) {
        public static StretchingSetResponse from(com.jllado.weightcontrol.domain.StretchingSet set) {
            return new StretchingSetResponse(set.getId(), set.getName(), set.getEntries().stream()
                .map(entry -> new StretchingSetEntryRequest(entry.getExercise().getId(), List.copyOf(entry.getDurations()), entry.getStretchingUnit(), List.copyOf(entry.getBreaths()))).toList());
        }
    }

    public static final class DurationMinutesDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.isExpectedNumberIntToken()) return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            return parser.getIntValue();
        }
    }

    public record WorkoutRequest(
        @NotNull LocalDate workoutDate,
        @Size(max = 500) String note,
        @NotEmpty List<@Valid WorkoutLineRequest> lines,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @DecimalMin("1") @JsonDeserialize(using = DurationMinutesDeserializer.class) Integer durationMinutes,
        @DecimalMin("0") @JsonDeserialize(using = DurationMinutesDeserializer.class) Integer warmUpMinutes,
        @DecimalMin("0") @JsonDeserialize(using = DurationMinutesDeserializer.class) Integer trainingMinutes,
        @DecimalMin("0") @JsonDeserialize(using = DurationMinutesDeserializer.class) Integer stretchingMinutes,
        @DecimalMin("0") @JsonDeserialize(using = DurationMinutesDeserializer.class) Integer cardioMinutes
    ) {
    }

    public record WorkoutLineRequest(
        @NotNull Long exerciseId,
        @DecimalMin("0") Integer calories,
        @DecimalMin("0") Integer averageHeartRate,
        @NotEmpty List<@Valid WorkoutSegmentRequest> segments,
        StretchingUnit stretchingUnit
    ) {
        public WorkoutLineRequest { if (stretchingUnit == null) stretchingUnit = StretchingUnit.SECONDS; }
    }

    public record WorkoutSegmentRequest(
        Integer repetitions,
        Integer durationSeconds,
        @DecimalMin("0.0") BigDecimal weight,
        @DecimalMin("0.0") BigDecimal speedKph,
        @DecimalMin("0.0") BigDecimal distanceKm,
        @DecimalMin("0.0") BigDecimal inclinePercent,
        @DecimalMin("0") Integer resistanceLevel,
        @DecimalMin("0") Integer calories,
        @JsonDeserialize(using = DurationMinutesDeserializer.class) Integer breaths
    ) {
    }

    public record WorkoutResponse(
        Long id,
        String sessionReference,
        String workoutDateFormat,
        LocalDate workoutDate,
        String note,
        List<WorkoutLineResponse> lines,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        Integer durationMinutes,
        Integer warmUpMinutes,
        Integer trainingMinutes,
        Integer stretchingMinutes,
        Integer cardioMinutes
    ) {
        public static WorkoutResponse from(Workout workout) {
            return new WorkoutResponse(
                workout.getId(),
                workout.getSessionReference(),
                DateTimes.formatDate(workout.getWorkoutDate()),
                workout.getWorkoutDate(),
                workout.getNote(),
                workout.getLines().stream().map(WorkoutLineResponse::from).toList(),
                workout.getStartTime(), workout.getDurationMinutes(), workout.getWarmUpMinutes(), workout.getTrainingMinutes(), workout.getStretchingMinutes(), workout.getCardioMinutes()
            );
        }
    }

    public record WorkoutDayResponse(LocalDate workoutDate, String workoutDateFormat, List<WorkoutResponse> sessions, WorkoutAssessmentResponse assessment) {}

    public record DashboardWorkoutResponse(
        List<WorkoutResponse> currentWorkouts,
        List<WorkoutResponse> previousWeekWorkouts,
        List<WorkoutResponse> preloadWorkouts,
        List<HistoryEventResponse> recordEvents,
        List<WorkoutDayResponse> days
    ) {
    }

    public record WorkoutDiaryPageResponse(
        List<WorkoutDayResponse> items,
        List<HistoryEventResponse> recordEvents,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
    }

    public record WorkoutLineResponse(
        Long exerciseId,
        String exerciseName,
        String exerciseDescription,
        ExerciseTrackingMode trackingMode,
        ExerciseType exerciseType,
        Integer position,
        Integer calories,
        Integer averageHeartRate,
        List<WorkoutSetResponse> sets,
        List<CardioIntervalResponse> intervals,
        StretchingUnit stretchingUnit
    ) {
        public static WorkoutLineResponse from(WorkoutLine line) {
            ExerciseTrackingMode mode = line.getExercise().getTrackingMode();
            List<WorkoutSetResponse> sets = mode == ExerciseTrackingMode.CARDIO
                ? List.of()
                : line.getSegments().stream().map(WorkoutSetResponse::from).toList();
            List<CardioIntervalResponse> intervals = mode == ExerciseTrackingMode.CARDIO
                ? line.getSegments().stream().map(CardioIntervalResponse::from).toList()
                : List.of();
            return new WorkoutLineResponse(
                line.getExercise().getId(),
                line.getExercise().getName(),
                line.getExercise().getDescription(),
                mode,
                line.getExercise().getExerciseType(),
                line.getPosition(),
                line.getCalories(),
                line.getAverageHeartRate(),
                sets,
                intervals,
                line.getStretchingUnit()
            );
        }
    }

    public record WorkoutSetResponse(
        Integer position,
        Integer repetitions,
        Integer durationSeconds,
        BigDecimal weight,
        Integer breaths
    ) {
        public static WorkoutSetResponse from(WorkoutSegment segment) {
            return new WorkoutSetResponse(
                segment.getPosition(),
                segment.getRepetitions(),
                segment.getDurationSeconds(),
                segment.getWeight(),
                segment.getBreaths()
            );
        }
    }

    public record CardioIntervalResponse(
        Integer position,
        Integer durationSeconds,
        BigDecimal speedKph,
        BigDecimal distanceKm,
        BigDecimal inclinePercent,
        Integer resistanceLevel
    ) {
        public static CardioIntervalResponse from(WorkoutSegment segment) {
            return new CardioIntervalResponse(
                segment.getPosition(),
                segment.getDurationSeconds(),
                segment.getSpeedKph(),
                segment.getDistanceKm(),
                segment.getInclinePercent(),
                segment.getResistanceLevel()
            );
        }
    }
}
