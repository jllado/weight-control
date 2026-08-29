package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CoachDtos.CoachCatalogResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachContextResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.ConfirmedRequest;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachHealthEntryResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.HealthEntryType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.CommonDtos.DeletionResponse;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeResponse;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.CoachBackPainEpisodeRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.CoachBackPainEpisodeUpdateRequest;
import com.jllado.weightcontrol.api.dto.BloodPressureDtos.BloodPressureResponse;
import com.jllado.weightcontrol.api.dto.BloodPressureDtos.CoachBloodPressureRequest;
import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachCoachingPlanRequest;
import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachingPlanResponse;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.CoachFastingPeriodRequest;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodResponse;
import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.CoachHealthConstraintRequest;
import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.HealthConstraintResponse;
import com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealResponse;
import com.jllado.weightcontrol.api.dto.LipidPanelDtos.CoachLipidPanelRequest;
import com.jllado.weightcontrol.api.dto.LipidPanelDtos.LipidPanelResponse;
import com.jllado.weightcontrol.api.dto.MoodDtos.CoachMoodRequest;
import com.jllado.weightcontrol.api.dto.MoodDtos.MoodResponse;
import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.OpenAiFileResponse;
import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.ProgressPhotoSetResponse;
import com.jllado.weightcontrol.api.dto.SicknessDtos.CoachSicknessRequest;
import com.jllado.weightcontrol.api.dto.SicknessDtos.SicknessResponse;
import com.jllado.weightcontrol.api.dto.SleepDtos.CoachSleepRequest;
import com.jllado.weightcontrol.api.dto.SleepDtos.SleepResponse;
import com.jllado.weightcontrol.api.dto.WeightDtos.CoachWeightRequest;
import com.jllado.weightcontrol.api.dto.WeightDtos.WeightResponse;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.SaveWorkoutAssessmentRequest;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentContextResponse;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentResponse;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.ProgressPhotoSide;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CoachingPlanService;
import com.jllado.weightcontrol.service.BackPainEpisodeService;
import com.jllado.weightcontrol.service.BloodPressureService;
import com.jllado.weightcontrol.service.FastingPeriodService;
import com.jllado.weightcontrol.service.HealthDataContextService;
import com.jllado.weightcontrol.service.HealthConstraintService;
import com.jllado.weightcontrol.service.MealService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.service.ProgressPhotoService;
import com.jllado.weightcontrol.service.LipidPanelService;
import com.jllado.weightcontrol.service.MoodService;
import com.jllado.weightcontrol.service.SicknessService;
import com.jllado.weightcontrol.service.SleepService;
import com.jllado.weightcontrol.service.WeightService;
import com.jllado.weightcontrol.service.BadRequestException;
import com.jllado.weightcontrol.service.WorkoutAssessmentService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatgpt-actions/coach")
public class ChatGptCoachActionController {

    private final HealthDataContextService healthDataContextService;
    private final HealthConstraintService healthConstraintService;
    private final CoachingPlanService coachingPlanService;
    private final MealService mealService;
    private final PersonalRecordMutationService personalRecordMutationService;
    private final FastingPeriodService fastingPeriodService;
    private final WorkoutAssessmentService workoutAssessmentService;
    private final ProgressPhotoService progressPhotoService;
    private final WeightService weightService;
    private final BloodPressureService bloodPressureService;
    private final MoodService moodService;
    private final SleepService sleepService;
    private final BackPainEpisodeService backPainEpisodeService;
    private final SicknessService sicknessService;
    private final LipidPanelService lipidPanelService;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;

    public ChatGptCoachActionController(
        HealthDataContextService healthDataContextService,
        HealthConstraintService healthConstraintService,
        CoachingPlanService coachingPlanService,
        MealService mealService,
        PersonalRecordMutationService personalRecordMutationService,
        FastingPeriodService fastingPeriodService,
        WorkoutAssessmentService workoutAssessmentService,
        ProgressPhotoService progressPhotoService,
        WeightService weightService,
        BloodPressureService bloodPressureService,
        MoodService moodService,
        SleepService sleepService,
        BackPainEpisodeService backPainEpisodeService,
        SicknessService sicknessService,
        LipidPanelService lipidPanelService,
        ObjectMapper objectMapper,
        CurrentUserService currentUserService
    ) {
        this.healthDataContextService = healthDataContextService;
        this.healthConstraintService = healthConstraintService;
        this.coachingPlanService = coachingPlanService;
        this.mealService = mealService;
        this.personalRecordMutationService = personalRecordMutationService;
        this.fastingPeriodService = fastingPeriodService;
        this.workoutAssessmentService = workoutAssessmentService;
        this.progressPhotoService = progressPhotoService;
        this.weightService = weightService;
        this.bloodPressureService = bloodPressureService;
        this.moodService = moodService;
        this.sleepService = sleepService;
        this.backPainEpisodeService = backPainEpisodeService;
        this.sicknessService = sicknessService;
        this.lipidPanelService = lipidPanelService;
        this.objectMapper = objectMapper;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/catalog")
    public CoachCatalogResponse getCoachCatalog() {
        return healthDataContextService.getCoachCatalog(currentUserService.requireUser());
    }

    @GetMapping("/context")
    public CoachContextResponse getHealthContext(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam Set<CoachDomain> domains,
        @RequestParam(defaultValue = "0") int recordsPage,
        @RequestParam(defaultValue = "25") int recordsPageSize
    ) {
        return healthDataContextService.getHealthContext(
            currentUserService.requireUser(),
            from,
            to,
            domains,
            recordsPage,
            recordsPageSize
        );
    }

    @GetMapping("/health-constraints")
    public List<HealthConstraintResponse> getHealthConstraints() {
        return healthConstraintService.findAll(currentUserService.requireUser()).stream()
            .map(HealthConstraintResponse::from)
            .toList();
    }

    @PostMapping("/health-constraints")
    public HealthConstraintResponse createHealthConstraint(
        @Valid @RequestBody CoachHealthConstraintRequest request
    ) {
        return HealthConstraintResponse.from(
            healthConstraintService.createConfirmed(currentUserService.requireUser(), request)
        );
    }

    @PutMapping("/health-constraints/{id}")
    public HealthConstraintResponse updateHealthConstraint(
        @PathVariable Long id,
        @Valid @RequestBody CoachHealthConstraintRequest request
    ) {
        return HealthConstraintResponse.from(
            healthConstraintService.updateConfirmed(currentUserService.requireUser(), id, request)
        );
    }

    @GetMapping("/active-plan")
    public ResponseEntity<CoachingPlanResponse> getActivePlan() {
        return coachingPlanService.find(currentUserService.requireUser())
            .map(CoachingPlanResponse::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/active-plan")
    public CoachingPlanResponse updateActivePlan(@Valid @RequestBody CoachCoachingPlanRequest request) {
        return CoachingPlanResponse.from(
            coachingPlanService.replaceConfirmed(currentUserService.requireUser(), request)
        );
    }

    @GetMapping("/meals")
    public List<MealResponse> getMeals(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        healthDataContextService.validateCoachDateRange(from, to);
        return mealService.findBetween(currentUserService.requireUser(), from, to).stream()
            .map(MealResponse::from)
            .toList();
    }

    @PostMapping("/meals")
    public MealResponse createMeal(@Valid @RequestBody CoachMealRequest request) {
        return MealResponse.from(personalRecordMutationService.createConfirmedMeal(currentUserService.requireUser(), request));
    }

    @PutMapping("/meals/{id}")
    public MealResponse updateMeal(@PathVariable Long id, @Valid @RequestBody CoachMealRequest request) {
        return MealResponse.from(personalRecordMutationService.updateConfirmedMeal(currentUserService.requireUser(), id, request));
    }

    @PostMapping("/meals/{id}/delete")
    public DeletionResponse deleteMeal(@PathVariable Long id, @Valid @RequestBody ConfirmedRequest request) {
        personalRecordMutationService.deleteConfirmedMeal(currentUserService.requireUser(), id, request.confirmed());
        return new DeletionResponse(true);
    }

    @GetMapping("/fasting-periods")
    public List<FastingPeriodResponse> getFastingPeriods(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        healthDataContextService.validateCoachDateRange(from, to);
        return fastingPeriodService.findBetween(currentUserService.requireUser(), from, to).stream()
            .map(FastingPeriodResponse::from)
            .toList();
    }

    @PostMapping("/fasting-periods")
    public FastingPeriodResponse createFastingPeriod(@Valid @RequestBody CoachFastingPeriodRequest request) {
        return FastingPeriodResponse.from(
            fastingPeriodService.createConfirmed(currentUserService.requireUser(), request)
        );
    }

    @PutMapping("/fasting-periods/{id}")
    public FastingPeriodResponse updateFastingPeriod(
        @PathVariable Long id,
        @Valid @RequestBody CoachFastingPeriodRequest request
    ) {
        return FastingPeriodResponse.from(
            fastingPeriodService.updateConfirmed(currentUserService.requireUser(), id, request)
        );
    }

    @PostMapping("/fasting-periods/{id}/delete")
    public DeletionResponse deleteFastingPeriod(@PathVariable Long id, @Valid @RequestBody ConfirmedRequest request) {
        fastingPeriodService.deleteConfirmed(currentUserService.requireUser(), id, request.confirmed());
        return new DeletionResponse(true);
    }

    @GetMapping("/workouts/{workoutDate}/assessment-context")
    public WorkoutAssessmentContextResponse getWorkoutAssessmentContext(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workoutDate
    ) {
        return workoutAssessmentService.getContext(currentUserService.requireUser(), workoutDate);
    }

    @PutMapping("/workouts/{workoutDate}/assessment")
    public WorkoutAssessmentResponse saveWorkoutAssessment(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workoutDate,
        @Valid @RequestBody SaveWorkoutAssessmentRequest request
    ) {
        return workoutAssessmentService.save(currentUserService.requireUser(), workoutDate, request);
    }

    @GetMapping("/progress-photos")
    public List<ProgressPhotoSetResponse> listProgressPhotos() {
        return progressPhotoService.findAll(currentUserService.requireUser());
    }

    @GetMapping("/progress-photos/{photoSetId}/files")
    public OpenAiFileResponse getProgressPhotoFiles(
        @PathVariable Long photoSetId,
        @RequestParam Set<ProgressPhotoSide> sides
    ) {
        return progressPhotoService.getFiles(currentUserService.requireUser(), photoSetId, sides);
    }

    @GetMapping("/weights")
    public List<WeightResponse> getWeights(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        healthDataContextService.validateCoachDateRange(from, to);
        return weightService.findBetween(currentUserService.requireUser(), from, to).stream()
            .map(weight -> WeightResponse.from(weight, null, null, null, weightService.getPerformanceWeek(weight)))
            .toList();
    }

    @PostMapping("/weights")
    public WeightResponse createWeight(@Valid @RequestBody CoachWeightRequest request) {
        var result = personalRecordMutationService.createWeight(currentUserService.requireUser(), request.weightRequest()).result();
        return WeightResponse.from(result, null, null, null, weightService.getPerformanceWeek(result));
    }

    @PutMapping("/weights/{id}")
    public WeightResponse updateWeight(@PathVariable Long id, @Valid @RequestBody CoachWeightRequest request) {
        var result = personalRecordMutationService.updateWeight(currentUserService.requireUser(), id, request.weightRequest()).result();
        return WeightResponse.from(result, null, null, null, weightService.getPerformanceWeek(result));
    }

    @GetMapping("/blood-pressures")
    public List<BloodPressureResponse> getBloodPressures(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        healthDataContextService.validateCoachDateRange(from, to);
        return bloodPressureService.findBetween(currentUserService.requireUser(), from, to).stream().map(BloodPressureResponse::from).toList();
    }

    @PostMapping("/blood-pressures")
    public BloodPressureResponse createBloodPressure(@Valid @RequestBody CoachBloodPressureRequest request) {
        return BloodPressureResponse.from(personalRecordMutationService.createBloodPressure(currentUserService.requireUser(), request.bloodPressure()).result());
    }

    @PutMapping("/blood-pressures/{id}")
    public BloodPressureResponse updateBloodPressure(@PathVariable Long id, @Valid @RequestBody CoachBloodPressureRequest request) {
        return BloodPressureResponse.from(personalRecordMutationService.updateBloodPressure(currentUserService.requireUser(), id, request.bloodPressure()).result());
    }

    @GetMapping("/moods")
    public List<MoodResponse> getMoods(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        healthDataContextService.validateCoachDateRange(from, to);
        return moodService.findBetween(currentUserService.requireUser(), from, to).stream().map(MoodResponse::from).toList();
    }

    @PostMapping("/moods")
    public MoodResponse createMood(@Valid @RequestBody CoachMoodRequest request) {
        return MoodResponse.from(personalRecordMutationService.createMood(currentUserService.requireUser(), request.mood()).result());
    }

    @PutMapping("/moods/{id}")
    public MoodResponse updateMood(@PathVariable Long id, @Valid @RequestBody CoachMoodRequest request) {
        return MoodResponse.from(personalRecordMutationService.updateMood(currentUserService.requireUser(), id, request.mood()).result());
    }

    @GetMapping("/sleeps")
    public List<SleepResponse> getSleeps(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        healthDataContextService.validateCoachDateRange(from, to);
        return sleepService.findBetween(currentUserService.requireUser(), from, to).stream().map(SleepResponse::from).toList();
    }

    @PostMapping("/sleeps")
    public SleepResponse createSleep(@Valid @RequestBody CoachSleepRequest request) {
        return SleepResponse.from(personalRecordMutationService.createSleep(currentUserService.requireUser(), request.sleep()).result());
    }

    @PutMapping("/sleeps/{id}")
    public SleepResponse updateSleep(@PathVariable Long id, @Valid @RequestBody CoachSleepRequest request) {
        return SleepResponse.from(personalRecordMutationService.updateSleep(currentUserService.requireUser(), id, request.sleep()).result());
    }

    @GetMapping("/back-pain-episodes")
    public List<BackPainEpisodeResponse> getBackPainEpisodes(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        healthDataContextService.validateCoachDateRange(from, to);
        return backPainEpisodeService.findBetween(currentUserService.requireUser(), from, to).stream().map(BackPainEpisodeResponse::from).toList();
    }

    @PostMapping("/back-pain-episodes")
    public BackPainEpisodeResponse createBackPainEpisode(@Valid @RequestBody CoachBackPainEpisodeRequest request) {
        return BackPainEpisodeResponse.from(backPainEpisodeService.create(currentUserService.requireUser(), request.episode()));
    }

    @PutMapping("/back-pain-episodes/{id}")
    public BackPainEpisodeResponse updateBackPainEpisode(@PathVariable Long id, @Valid @RequestBody CoachBackPainEpisodeUpdateRequest request) {
        return BackPainEpisodeResponse.from(backPainEpisodeService.update(currentUserService.requireUser(), id, request.episode()));
    }

    @GetMapping("/sicknesses")
    public List<SicknessResponse> getSicknesses(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        healthDataContextService.validateCoachDateRange(from, to);
        return sicknessService.findBetween(currentUserService.requireUser(), from, to).stream().map(SicknessResponse::from).toList();
    }

    @PostMapping("/sicknesses")
    public SicknessResponse createSickness(@Valid @RequestBody CoachSicknessRequest request) {
        return SicknessResponse.from(sicknessService.create(currentUserService.requireUser(), request.sickness()));
    }

    @PutMapping("/sicknesses/{id}")
    public SicknessResponse updateSickness(@PathVariable Long id, @Valid @RequestBody CoachSicknessRequest request) {
        return SicknessResponse.from(sicknessService.update(currentUserService.requireUser(), id, request.sickness()));
    }

    @GetMapping("/lipid-panels")
    public List<LipidPanelResponse> getLipidPanels(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        healthDataContextService.validateCoachDateRange(from, to);
        return lipidPanelService.findBetween(currentUserService.requireUser(), from, to).stream().map(LipidPanelResponse::from).toList();
    }

    @PostMapping("/lipid-panels")
    public LipidPanelResponse createLipidPanel(@Valid @RequestBody CoachLipidPanelRequest request) {
        return LipidPanelResponse.from(personalRecordMutationService.createLipidPanel(currentUserService.requireUser(), request.lipidPanel()).result());
    }

    @PutMapping("/lipid-panels/{id}")
    public LipidPanelResponse updateLipidPanel(@PathVariable Long id, @Valid @RequestBody CoachLipidPanelRequest request) {
        return LipidPanelResponse.from(personalRecordMutationService.updateLipidPanel(currentUserService.requireUser(), id, request.lipidPanel()).result());
    }

    @GetMapping("/health-entries/{entryType}")
    public List<CoachHealthEntryResponse> getHealthEntries(
        @PathVariable("entryType") HealthEntryType type,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        healthDataContextService.validateCoachDateRange(from, to);
        var user = currentUserService.requireUser();
        return switch (type) {
            case WEIGHT -> weightService.findBetween(user, from, to).stream().map(entry -> new CoachHealthEntryResponse(type, WeightResponse.from(entry, null, null, null, weightService.getPerformanceWeek(entry)))).toList();
            case BLOOD_PRESSURE -> bloodPressureService.findBetween(user, from, to).stream().map(entry -> new CoachHealthEntryResponse(type, BloodPressureResponse.from(entry))).toList();
            case MOOD -> moodService.findBetween(user, from, to).stream().map(entry -> new CoachHealthEntryResponse(type, MoodResponse.from(entry))).toList();
            case SLEEP -> sleepService.findBetween(user, from, to).stream().map(entry -> new CoachHealthEntryResponse(type, SleepResponse.from(entry))).toList();
            case BACK_PAIN_EPISODE -> backPainEpisodeService.findBetween(user, from, to).stream().map(entry -> new CoachHealthEntryResponse(type, BackPainEpisodeResponse.from(entry))).toList();
            case SICKNESS -> sicknessService.findBetween(user, from, to).stream().map(entry -> new CoachHealthEntryResponse(type, SicknessResponse.from(entry))).toList();
            case LIPID_PANEL -> lipidPanelService.findBetween(user, from, to).stream().map(entry -> new CoachHealthEntryResponse(type, LipidPanelResponse.from(entry))).toList();
        };
    }

    @PostMapping("/health-entries/{entryType}")
    public CoachHealthEntryResponse createHealthEntry(@PathVariable("entryType") HealthEntryType type, @RequestBody JsonNode request) {
        requireConfirmation(request);
        return new CoachHealthEntryResponse(type, createEntry(type, request));
    }

    @PutMapping("/health-entries/{entryType}/{id}")
    public CoachHealthEntryResponse updateHealthEntry(@PathVariable("entryType") HealthEntryType type, @PathVariable Long id, @RequestBody JsonNode request) {
        requireConfirmation(request);
        return new CoachHealthEntryResponse(type, updateEntry(type, id, request));
    }

    private Object createEntry(HealthEntryType type, JsonNode request) {
        return switch (type) {
            case WEIGHT -> createWeight(read(request, CoachWeightRequest.class));
            case BLOOD_PRESSURE -> createBloodPressure(read(request, CoachBloodPressureRequest.class));
            case MOOD -> createMood(read(request, CoachMoodRequest.class));
            case SLEEP -> createSleep(read(request, CoachSleepRequest.class));
            case BACK_PAIN_EPISODE -> createBackPainEpisode(read(request, CoachBackPainEpisodeRequest.class));
            case SICKNESS -> createSickness(read(request, CoachSicknessRequest.class));
            case LIPID_PANEL -> createLipidPanel(read(request, CoachLipidPanelRequest.class));
        };
    }

    private Object updateEntry(HealthEntryType type, Long id, JsonNode request) {
        return switch (type) {
            case WEIGHT -> updateWeight(id, read(request, CoachWeightRequest.class));
            case BLOOD_PRESSURE -> updateBloodPressure(id, read(request, CoachBloodPressureRequest.class));
            case MOOD -> updateMood(id, read(request, CoachMoodRequest.class));
            case SLEEP -> updateSleep(id, read(request, CoachSleepRequest.class));
            case BACK_PAIN_EPISODE -> updateBackPainEpisode(id, read(request, CoachBackPainEpisodeUpdateRequest.class));
            case SICKNESS -> updateSickness(id, read(request, CoachSicknessRequest.class));
            case LIPID_PANEL -> updateLipidPanel(id, read(request, CoachLipidPanelRequest.class));
        };
    }

    private <T> T read(JsonNode request, Class<T> type) {
        return objectMapper.convertValue(request, type);
    }

    private void requireConfirmation(JsonNode request) {
        if (!request.path("confirmed").asBoolean()) {
            throw new BadRequestException("Explicit confirmation is required");
        }
    }
}
