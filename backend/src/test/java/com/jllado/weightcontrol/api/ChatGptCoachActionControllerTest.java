package com.jllado.weightcontrol.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jllado.weightcontrol.domain.InAppNotification;
import com.jllado.weightcontrol.service.GptActionNotificationService;
import com.jllado.weightcontrol.service.InAppNotificationService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachCatalogResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachContextResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachDataSemantics;
import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.OpenAiFileResponse;
import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.ProgressPhotoSetResponse;
import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.api.dto.WeightDtos.WeightRequest;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentResponse;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentWeeklyPlanData;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentPlannedDayData;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentContextResponse;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentDayData;
import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.DishUnit;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.HealthConstraintSource;
import com.jllado.weightcontrol.domain.HealthConstraintType;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealDish;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.ProgressPhotoSide;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.CoachWorkoutEntry;
import com.jllado.weightcontrol.api.dto.DishRecipeDtos.RecipeResponse;
import com.jllado.weightcontrol.api.dto.DishRecipeDtos.RecipeRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.DishReference;
import com.jllado.weightcontrol.api.dto.MealDtos.MealDishRequest;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.BadRequestException;
import com.jllado.weightcontrol.service.NotFoundException;
import com.jllado.weightcontrol.service.CoachingPlanService;
import com.jllado.weightcontrol.service.CoachNoteService;
import com.jllado.weightcontrol.service.FastingPeriodService;
import com.jllado.weightcontrol.service.HealthConstraintService;
import com.jllado.weightcontrol.service.HealthDataContextService;
import com.jllado.weightcontrol.service.MealService;
import com.jllado.weightcontrol.service.DishRecipeService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService.MutationResult;
import com.jllado.weightcontrol.service.ProgressPhotoService;
import com.jllado.weightcontrol.service.WorkoutAssessmentService;
import com.jllado.weightcontrol.service.WorkoutService;
import com.jllado.weightcontrol.service.BackPainEpisodeService;
import com.jllado.weightcontrol.service.BloodPressureService;
import com.jllado.weightcontrol.service.LipidPanelService;
import com.jllado.weightcontrol.service.MoodService;
import com.jllado.weightcontrol.service.SicknessService;
import com.jllado.weightcontrol.service.SleepService;
import com.jllado.weightcontrol.service.WeightService;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.validation.Validation;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class ChatGptCoachActionControllerTest {

    @Mock
    private HealthDataContextService healthDataContextService;
    @Mock
    private HealthConstraintService healthConstraintService;
    @Mock
    private CoachingPlanService coachingPlanService;
    @Mock
    private MealService mealService;
    @Mock
    private DishRecipeService dishRecipeService;
    @Mock
    private CoachNoteService coachNoteService;
    @Mock
    private PersonalRecordMutationService personalRecordMutationService;
    @Mock
    private FastingPeriodService fastingPeriodService;
    @Mock
    private WorkoutAssessmentService workoutAssessmentService;
    @Mock
    private WorkoutService workoutService;
    @Mock
    private ProgressPhotoService progressPhotoService;
    @Mock
    private WeightService weightService;
    @Mock
    private BloodPressureService bloodPressureService;
    @Mock
    private MoodService moodService;
    @Mock
    private SleepService sleepService;
    @Mock
    private BackPainEpisodeService backPainEpisodeService;
    @Mock
    private SicknessService sicknessService;
    @Mock
    private LipidPanelService lipidPanelService;
    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private InAppNotificationService notifications;
    @Mock
    private ApplicationEventPublisher events;

    @Mock private com.jllado.weightcontrol.service.WorkoutPlanService workoutPlans;
    private GptActionNotificationService actionNotifications;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        org.mockito.Mockito.lenient().when(notifications.recordGptAction(any(), any(), any())).thenAnswer(invocation -> {
            var notification = new InAppNotification();
            notification.setId(50L);
            notification.setTitle("Weight Control Coach");
            notification.setDeduplicationKey("GPT_ACTION:test");
            return notification;
        });
        actionNotifications = org.mockito.Mockito.spy(new GptActionNotificationService(notifications, events));
        ChatGptCoachActionController controller = new ChatGptCoachActionController(
            healthDataContextService,
            healthConstraintService,
            coachingPlanService,
            coachNoteService,
            mealService,
            dishRecipeService,
            personalRecordMutationService,
            fastingPeriodService,
            workoutAssessmentService,
            workoutService,
            progressPhotoService,
            weightService,
            bloodPressureService,
            moodService,
            sleepService,
            backPainEpisodeService,
            sicknessService,
            lipidPanelService,
            objectMapper,
            currentUserService,
            actionNotifications,
            Validation.buildDefaultValidatorFactory().getValidator()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller, new ChatGptWorkoutPlanActionController(workoutPlans, currentUserService, actionNotifications))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
        user = new User();
        user.setId(1L);
    }

    @Test
    void savesCoachNoteOnlyAfterConfirmation() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        var note = new com.jllado.weightcontrol.domain.CoachNote();
        note.setId(12L); note.setNoteDate(LocalDate.of(2026, 9, 20)); note.setContent("Review sleep");
        when(coachNoteService.create(eq(user), any())).thenReturn(note);
        mockMvc.perform(post("/api/chatgpt-actions/coach/coach-notes").contentType("application/json").content("{\"date\":\"2026-09-20\",\"content\":\"Review sleep\",\"confirmed\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Review sleep"));
        verify(notifications).recordGptAction(user, "Coach note saved", "/coach-notes");
        mockMvc.perform(post("/api/chatgpt-actions/coach/coach-notes").contentType("application/json").content("{\"date\":\"2026-09-20\",\"content\":\"Review sleep\",\"confirmed\":false}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void workoutTargetDispatchesToConfirmedWeeklyPlanWithoutChangingCoachingPlan() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(workoutPlans.editContext(user)).thenReturn(new com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutPlanEditContext(null, List.of()));
        mockMvc.perform(get("/api/chatgpt-actions/coach/active-plan").param("target", "WORKOUT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.plan").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.exercises").isArray());
        var days = java.util.Arrays.stream(java.time.DayOfWeek.values()).map(day -> "{\"day\":\"" + day + "\",\"rest\":true,\"lines\":[]}").collect(java.util.stream.Collectors.joining(","));
        String request = "{\"plan\":{\"startDate\":\"2026-09-14\",\"reviewDate\":\"2026-10-26\",\"days\":[" + days + "]},\"updateToken\":\"retrieved-token\",\"confirmed\":true,\"saunaSchemaVersion\":1}";
        mockMvc.perform(put("/api/chatgpt-actions/coach/active-plan").param("target", "WORKOUT").contentType("application/json").content(request))
            .andExpect(status().isOk());
        var captured = ArgumentCaptor.forClass(com.jllado.weightcontrol.api.dto.WorkoutDtos.CoachWorkoutPlanUpdateRequest.class);
        verify(workoutPlans).updateConfirmed(eq(user), captured.capture());
        assertEquals("retrieved-token", captured.getValue().updateToken());
        assertEquals(1, captured.getValue().saunaSchemaVersion());
        assertEquals(7, captured.getValue().plan().days().size());
        verify(notifications).recordGptAction(user, "Workout plan updated", "/workouts?tab=plan");
        verifyNoInteractions(coachingPlanService);
        mockMvc.perform(put("/api/chatgpt-actions/coach/active-plan").param("target", "WORKOUT").contentType("application/json").content(request.replace("\"confirmed\":true", "\"confirmed\":false")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/chatgpt-actions/coach/active-plan").param("target", "WORKOUT").contentType("application/json").content(request.replace(",\"saunaSchemaVersion\":1", "")))
            .andExpect(status().isBadRequest());
        verify(workoutPlans, org.mockito.Mockito.times(1)).updateConfirmed(eq(user), any());
    }

    @Test
    void workoutExerciseQueryReturnsOnlyMatchingChoicesWithoutThePlan() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        var choice = new com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutPlanExerciseChoice(7L, "Push-up", "Press from floor", com.jllado.weightcontrol.domain.ExerciseTrackingMode.REPS, com.jllado.weightcontrol.domain.ExerciseType.TRAINING, null);
        when(workoutPlans.searchExercises("push")).thenReturn(new com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutExerciseSearchResult("push", List.of(choice)));

        var response = mockMvc.perform(get("/api/chatgpt-actions/coach/active-plan").param("target", "WORKOUT").param("exerciseQuery", "push"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.query").value("push"))
            .andExpect(jsonPath("$.exercises[0].id").value(7))
            .andReturn().getResponse().getContentAsString();
        var names = new java.util.HashSet<String>();
        new ObjectMapper().readTree(response).fieldNames().forEachRemaining(names::add);
        assertEquals(java.util.Set.of("query", "exercises"), names);
    }

    @Test
    void genericSleepLookupPreservesDedicatedRecordsAndEditableIds() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 20);
        Sleep sleep = new Sleep();
        sleep.setId(9L);
        sleep.setUser(user);
        sleep.setSleepDate(date);
        sleep.setTotalSleepDuration(19080);
        sleep.setAverageHeartRate(new BigDecimal("55"));
        sleep.setAverageHrv(42);
        when(currentUserService.requireUser()).thenReturn(user);
        when(sleepService.findBetween(user, date, date)).thenReturn(List.of(sleep));
        String dedicated = mockMvc.perform(get("/api/chatgpt-actions/coach/sleeps")
                .param("from", date.toString()).param("to", date.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String generic = mockMvc.perform(get("/api/chatgpt-actions/coach/health-entries/SLEEP")
                .param("from", date.toString()).param("to", date.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("SLEEP"))
            .andExpect(jsonPath("$[0].entry.id").value(9))
            .andExpect(jsonPath("$[0].entry.totalSleepDuration").value(19080))
            .andReturn().getResponse().getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(mapper.readTree(dedicated).get(0), mapper.readTree(generic).get(0).get("entry"));
        verify(sleepService, org.mockito.Mockito.times(2)).findBetween(user, date, date);
        verify(healthDataContextService, org.mockito.Mockito.times(2)).validateCoachDateRange(date, date);
    }

    @Test
    void genericDishRecipeLookupDoesNotRequireDates() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(dishRecipeService.findAll(user)).thenReturn(List.of(savedRecipe()));
        mockMvc.perform(get("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("DISH_RECIPE"))
            .andExpect(jsonPath("$[0].entry.id").value(41))
            .andExpect(jsonPath("$[0].entry.name").value("Fruit bowl"))
            .andExpect(jsonPath("$[0].entry.servings").value(2.5))
            .andExpect(jsonPath("$[0].entry.ingredients[0].name").value("Blueberries"))
            .andExpect(jsonPath("$[0].entry.ingredients[0].quantity").value(50))
            .andExpect(jsonPath("$[0].entry.ingredients[0].unit").value("GRAM"))
            .andExpect(jsonPath("$[0].entry.ingredients[0].fruit").value(true))
            .andExpect(jsonPath("$[0].entry.ingredients[0].reference.calories").value(90))
            .andExpect(jsonPath("$[0].entry.ingredients[0].vitaminDMicrograms").value(0.05))
            .andExpect(jsonPath("$[0].entry.ingredients[0].omega3Milligrams").value(2.5))
            .andExpect(jsonPath("$[0].entry.ingredients[0].magnesiumMilligrams").value(5))
            .andExpect(jsonPath("$[0].entry.ingredients[0].nutrientSource").value("Saved composition"))
            .andExpect(jsonPath("$[0].entry.ingredients[0].nutrientsEstimated").value(false))
            .andExpect(jsonPath("$[0].entry.ingredients[1].name").value("Yogurt"))
            .andExpect(jsonPath("$[0].entry.ingredients[1].fruit").value(false))
            .andExpect(jsonPath("$[0].entry.ingredients[2]").doesNotExist())
            .andExpect(jsonPath("$[0].entry.userId").doesNotExist());
        verify(dishRecipeService).findAll(user);
        verifyNoInteractions(healthDataContextService);
    }

    @Test
    void genericWorkoutLookupRequiresBoundedDates() throws Exception {
        var date = LocalDate.of(2026, 8, 20);
        when(currentUserService.requireUser()).thenReturn(user);
        when(workoutService.findCoachEntries(user, date, date)).thenReturn(List.of());
        mockMvc.perform(get("/api/chatgpt-actions/coach/health-entries/WORKOUT")
                .param("from", date.toString()).param("to", date.toString()))
            .andExpect(status().isOk());
        verify(workoutService).findCoachEntries(user, date, date);
    }

    @Test
    void genericWorkoutCreateReturnsCompleteSaunaRecordAndNotifiesOnce() throws Exception {
        var workout = savedSaunaWorkout();
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.createCoachWorkout(eq(user), any()))
            .thenReturn(new PersonalRecordMutationService.CoachWorkoutCreateResult(workout, true));
        when(workoutService.coachEntry(user, workout)).thenReturn(CoachWorkoutEntry.from(workout, false));

        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/WORKOUT")
                .contentType("application/json").content(coachSaunaJson(true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("WORKOUT"))
            .andExpect(jsonPath("$.entry.id").value(41))
            .andExpect(jsonPath("$.entry.updateToken").value(0))
            .andExpect(jsonPath("$.entry.workout.saunaRoundsMinutes[0]").value(12))
            .andExpect(jsonPath("$.entry.workout.plannedSaunaRoundsMinutes[0]").value(10));
        verify(notifications).recordGptAction(user, "Workout saved", "/workouts?tab=diary");
    }

    @Test
    void genericWorkoutUpdateRequiresTokenAndPreservesActualSaunaMinutes() throws Exception {
        var workout = savedSaunaWorkout();
        workout.setRevision(2);
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.updateCoachWorkout(eq(user), eq(41L), eq(1L), any()))
            .thenReturn(new MutationResult<>(workout, List.of()));
        when(workoutService.coachEntry(user, workout)).thenReturn(CoachWorkoutEntry.from(workout, false));

        mockMvc.perform(put("/api/chatgpt-actions/coach/health-entries/WORKOUT/41")
                .contentType("application/json").content(coachSaunaJson(true).replace("\"confirmed\":true", "\"confirmed\":true,\"updateToken\":1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entry.updateToken").value(2))
            .andExpect(jsonPath("$.entry.workout.saunaRoundsMinutes[1]").value(8));
        verify(notifications).recordGptAction(user, "Workout updated", "/workouts?tab=diary");
    }

    @Test
    void genericWorkoutWritesRequireConfirmationAndCurrentToken() throws Exception {
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/WORKOUT")
                .contentType("application/json").content(coachSaunaJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/chatgpt-actions/coach/health-entries/WORKOUT/41")
                .contentType("application/json").content(coachSaunaJson(true)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(personalRecordMutationService);
    }

    private Workout savedSaunaWorkout() {
        var workout = new Workout();
        workout.setId(41L);
        workout.setWorkoutDate(LocalDate.of(2026, 8, 20));
        workout.setRecordingKey("f04f3d14-c6a7-4e8a-a896-1fd1f1f812f1");
        workout.setDurationMinutes(20);
        workout.setSaunaSession(true);
        workout.setSaunaRoundsMinutes(List.of(12, 8));
        workout.setPlannedSaunaRoundsMinutes(List.of(10, 10));
        return workout;
    }

    private String coachSaunaJson(boolean confirmed) {
        return "{\"confirmed\":" + confirmed + ",\"workout\":{\"workoutDate\":\"2026-08-20\",\"lines\":[]," +
            "\"durationMinutes\":20,\"saunaSession\":true,\"saunaRoundsMinutes\":[12,8],\"plannedSaunaRoundsMinutes\":[10,10]," +
            "\"recordingKey\":\"f04f3d14-c6a7-4e8a-a896-1fd1f1f812f1\"}}";
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "WEIGHT", "BLOOD_PRESSURE", "MOOD", "SLEEP", "BACK_PAIN_EPISODE", "SICKNESS", "LIPID_PANEL", "WORKOUT"
    })
    void genericDatedLookupStillRequiresBothDates(String type) throws Exception {
        String path = "/api/chatgpt-actions/coach/health-entries/" + type;
        mockMvc.perform(get(path)).andExpect(status().isBadRequest());
        mockMvc.perform(get(path).param("from", "2026-08-20")).andExpect(status().isBadRequest());
        mockMvc.perform(get(path).param("to", "2026-08-20")).andExpect(status().isBadRequest());
        verifyNoInteractions(healthDataContextService);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "WEIGHT", "BLOOD_PRESSURE", "MOOD", "SLEEP", "BACK_PAIN_EPISODE", "SICKNESS", "LIPID_PANEL"
    })
    void genericDatedLookupStillRejectsInvalidFutureAndOversizedRanges(String type) throws Exception {
        org.mockito.Mockito.doCallRealMethod().when(healthDataContextService)
            .validateCoachDateRange(any(LocalDate.class), any(LocalDate.class));
        String path = "/api/chatgpt-actions/coach/health-entries/" + type;
        LocalDate today = LocalDate.now(DateTimes.USER_ZONE);
        mockMvc.perform(get(path).param("from", today.toString()).param("to", today.minusDays(1).toString()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(path).param("from", today.minusDays(90).toString()).param("to", today.toString()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(path).param("from", today.toString()).param("to", today.plusDays(1).toString()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void genericDishRecipeCreateReturnsSavedFullRecipeAndDishesNotification() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(dishRecipeService.create(eq(user), any(RecipeRequest.class))).thenReturn(savedRecipe());
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE")
                .contentType("application/json").content(coachRecipeJson(true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("DISH_RECIPE"))
            .andExpect(jsonPath("$.entry.id").value(41))
            .andExpect(jsonPath("$.entry.name").value("Fruit bowl"))
            .andExpect(jsonPath("$.entry.servings").value(2.5))
            .andExpect(jsonPath("$.entry.ingredients[0].fruit").value(true))
            .andExpect(jsonPath("$.entry.ingredients[0].reference.quantity").value(100))
            .andExpect(jsonPath("$.entry.ingredients[0].nutrientSource").value("Saved composition"))
            .andExpect(jsonPath("$.entry.ingredients[1].name").value("Yogurt"))
            .andExpect(jsonPath("$.entry.ingredients[1].fruit").value(false))
            .andExpect(jsonPath("$.entry.ingredients[2]").doesNotExist());
        var captured = ArgumentCaptor.forClass(RecipeRequest.class);
        verify(dishRecipeService).create(eq(user), captured.capture());
        var recipe = captured.getValue();
        assertEquals("Fruit bowl", recipe.name());
        assertEquals(0, recipe.servings().compareTo(new BigDecimal("2.5")));
        assertEquals(2, recipe.ingredients().size());
        var ingredient = recipe.ingredients().getFirst();
        assertEquals("Blueberries", ingredient.name());
        assertEquals(0, ingredient.quantity().compareTo(new BigDecimal("50")));
        assertEquals(DishUnit.GRAM, ingredient.unit());
        assertTrue(ingredient.fruit());
        assertEquals(90, ingredient.reference().calories());
        assertEquals(0, ingredient.vitaminDMicrograms().compareTo(new BigDecimal("0.05")));
        assertEquals(0, ingredient.omega3Milligrams().compareTo(new BigDecimal("2.50")));
        assertEquals(0, ingredient.magnesiumMilligrams().compareTo(new BigDecimal("5.00")));
        assertEquals("Saved composition", ingredient.nutrientSource());
        assertEquals(false, ingredient.nutrientsEstimated());
        assertEquals("Yogurt", recipe.ingredients().get(1).name());
        assertEquals(false, recipe.ingredients().get(1).fruit());
        verify(notifications).recordGptAction(user, "Dish saved", "/calories?tab=dishes");
        verifyNoInteractions(mealService, personalRecordMutationService);
    }

    @Test
    void genericDishRecipeUpdateReplacesOwnedRecipeAndReturnsSavedFullRecipe() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(dishRecipeService.update(eq(user), eq(41L), any(RecipeRequest.class))).thenReturn(savedRecipe());
        mockMvc.perform(put("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE/41")
                .contentType("application/json").content(coachRecipeJson(true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entry.id").value(41))
            .andExpect(jsonPath("$.entry.ingredients[0].name").value("Blueberries"))
            .andExpect(jsonPath("$.entry.ingredients[0].fruit").value(true))
            .andExpect(jsonPath("$.entry.ingredients[0].magnesiumMilligrams").value(5))
            .andExpect(jsonPath("$.entry.ingredients[1].name").value("Yogurt"))
            .andExpect(jsonPath("$.entry.ingredients[1].fruit").value(false));
        var captured = ArgumentCaptor.forClass(RecipeRequest.class);
        verify(dishRecipeService).update(eq(user), eq(41L), captured.capture());
        assertEquals("Fruit bowl", captured.getValue().name());
        assertEquals(0, captured.getValue().ingredients().getFirst().reference().omega3Milligrams()
            .compareTo(new BigDecimal("5.00")));
        assertEquals(List.of("Blueberries", "Yogurt"), captured.getValue().ingredients().stream()
            .map(MealDishRequest::name).toList());
        verify(notifications).recordGptAction(user, "Dish updated", "/calories?tab=dishes");
        verifyNoInteractions(mealService, personalRecordMutationService);
    }

    @Test
    void genericDishRecipeWritesSupportFreshOwnedReadsAfterEachWrite() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(dishRecipeService.create(eq(user), any(RecipeRequest.class))).thenReturn(savedRecipe());
        when(dishRecipeService.update(eq(user), eq(41L), any(RecipeRequest.class))).thenReturn(savedRecipe());
        when(dishRecipeService.findAll(user)).thenReturn(List.of(savedRecipe()));
        var mapper = new ObjectMapper();
        var create = mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE")
                .contentType("application/json").content(coachRecipeJson(true)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var afterCreate = mockMvc.perform(get("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(create), mapper.readTree(afterCreate).get(0));
        var update = mockMvc.perform(put("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE/41")
                .contentType("application/json").content(coachRecipeJson(true)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var afterUpdate = mockMvc.perform(get("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(update), mapper.readTree(afterUpdate).get(0));
        verify(dishRecipeService, org.mockito.Mockito.times(2)).findAll(user);
    }

    @Test
    void genericDishRecipeWritesRejectUnconfirmedInvalidDuplicateAndCrossOwnerRequests() throws Exception {
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE")
                .contentType("application/json").content(coachRecipeJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE/41")
                .contentType("application/json").content(coachRecipeJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE")
                .contentType("application/json").content(coachRecipeJson(true).replace("\"servings\":2.5", "\"servings\":0")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE")
                .contentType("application/json").content("{\"confirmed\":true}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(dishRecipeService, notifications);

        when(currentUserService.requireUser()).thenReturn(user);
        when(dishRecipeService.create(eq(user), any(RecipeRequest.class)))
            .thenThrow(new BadRequestException("A dish with this name already exists."));
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE")
                .contentType("application/json").content(coachRecipeJson(true)))
            .andExpect(status().isBadRequest());
        when(dishRecipeService.update(eq(user), eq(99L), any(RecipeRequest.class)))
            .thenThrow(new NotFoundException("Dish not found"));
        mockMvc.perform(put("/api/chatgpt-actions/coach/health-entries/DISH_RECIPE/99")
                .contentType("application/json").content(coachRecipeJson(true)))
            .andExpect(status().isNotFound());
        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @CsvSource({
        "weights,WEIGHT,Weight,/?tab=body&date=2026-08-20&metric=weight", "blood-pressures,BLOOD_PRESSURE,Blood pressure,/?tab=body&date=2026-08-20&metric=blood-pressure",
        "moods,MOOD,Mood,/?tab=mood&date=2026-08-20", "sleeps,SLEEP,Sleep,/?tab=sleep&date=2026-08-20",
        "back-pain-episodes,BACK_PAIN_EPISODE,Back check-in,/?tab=back&date=2026-08-20",
        "sicknesses,SICKNESS,Sickness,/sicknesses", "lipid-panels,LIPID_PANEL,Lipid panel,/?tab=body&date=2026-08-20&metric=lipids"
    })
    void dedicatedAndGenericHealthWritesUseOneNotificationBoundary(String path, String type, String concept, String destination) throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        if (type.equals("BACK_PAIN_EPISODE")) {
            BackPainEpisode existing = new BackPainEpisode();
            existing.setEpisodeDate(LocalDate.of(2026, 8, 20));
            when(backPainEpisodeService.requireOwned(user, 9L)).thenReturn(existing);
        }
        org.mockito.Mockito.doReturn(null).when(actionNotifications).execute(eq(user), any(), any(), any());
        String payload = switch (type) {
            case "WEIGHT" -> weightJson(true);
            case "BLOOD_PRESSURE" -> bloodPressureJson(true);
            case "MOOD" -> moodJson(true);
            case "SLEEP" -> sleepJson(true);
            case "BACK_PAIN_EPISODE" -> backPainJson(true);
            case "SICKNESS" -> sicknessJson(true);
            case "LIPID_PANEL" -> lipidPanelJson(true);
            default -> throw new IllegalArgumentException(type);
        };
        for (String endpoint : List.of(path, "health-entries/" + type)) {
            mockMvc.perform(post("/api/chatgpt-actions/coach/" + endpoint).contentType("application/json").content(payload))
                .andExpect(status().isOk());
            String updatePayload = type.equals("BACK_PAIN_EPISODE") ? payload.replace("\"date\":\"2026-08-20\",", "") : payload;
            mockMvc.perform(put("/api/chatgpt-actions/coach/" + endpoint + "/9").contentType("application/json").content(updatePayload))
                .andExpect(status().isOk());
        }
        verify(actionNotifications, org.mockito.Mockito.times(2)).execute(eq(user), eq(concept + " saved"), eq(destination), any());
        verify(actionNotifications, org.mockito.Mockito.times(2)).execute(eq(user), eq(concept + " updated"), eq(destination), any());
        verifyNoInteractions(notifications, events);
    }

    @Test
    void catalogResolvesCurrentUserAndReturnsMetadata() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-16T10:15:00+02:00");
        CoachCatalogResponse response = new CoachCatalogResponse("Europe/Madrid", now, null, List.of());
        when(currentUserService.requireUser()).thenReturn(user);
        when(healthDataContextService.getCoachCatalog(user)).thenReturn(response);

        mockMvc.perform(get("/api/chatgpt-actions/coach/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Europe/Madrid"));

        verify(healthDataContextService).getCoachCatalog(user);
    }

    @ParameterizedTest
    @CsvSource({"BODY,TRAINING", "DISHES,FOODS"})
    void contextParsesCommaSeparatedDomains(CoachDomain first, CoachDomain second) throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 16);
        when(currentUserService.requireUser()).thenReturn(user);
        CoachContextResponse response = new CoachContextResponse(
            "Europe/Madrid",
            OffsetDateTime.parse("2026-08-16T10:15:00+02:00"),
            from,
            to,
            LocalDate.of(2026, 8, 15),
            false,
            new CoachDataSemantics(true, true, true, true),
            new LinkedHashMap<>()
        );
        when(healthDataContextService.getHealthContext(
            user,
            from,
            to,
            Set.of(first, second),
            0,
            25,
            null
        )).thenReturn(response);

        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-01")
                .param("to", "2026-08-16")
                .param("domains", first + "," + second))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endDateComplete").value(false));

        verify(healthDataContextService).getHealthContext(
            user,
            from,
            to,
            Set.of(first, second),
            0,
            25,
            null
        );
    }

    @Test
    void contextForwardsFoodQueryToScopedHealthContext() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 16);
        when(currentUserService.requireUser()).thenReturn(user);
        CoachContextResponse response = new CoachContextResponse(
            "Europe/Madrid", OffsetDateTime.parse("2026-08-16T10:15:00+02:00"), date, date,
            null, false, new CoachDataSemantics(true, true, true, true), new LinkedHashMap<>()
        );
        when(healthDataContextService.getHealthContext(user, date, date, Set.of(CoachDomain.FOODS), 0, 25, "Rice"))
            .thenReturn(response);

        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-16")
                .param("to", "2026-08-16")
                .param("domains", "FOODS")
                .param("foodQuery", "Rice"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("Europe/Madrid"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "a", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void contextRejectsInvalidFoodQuery(String foodQuery) throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 16);
        when(currentUserService.requireUser()).thenReturn(user);
        when(healthDataContextService.getHealthContext(user, date, date, Set.of(CoachDomain.FOODS), 0, 25, foodQuery))
            .thenThrow(new com.jllado.weightcontrol.service.BadRequestException("Food query must be between 2 and 80 characters"));

        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-16")
                .param("to", "2026-08-16")
                .param("domains", "FOODS")
                .param("foodQuery", foodQuery))
            .andExpect(status().isBadRequest());
    }

    @Test
    void contextRejectsMissingInvalidAndEmptyDomains() throws Exception {
        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-01")
                .param("to", "2026-08-16"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-01")
                .param("to", "2026-08-16")
                .param("domains", "UNKNOWN"))
            .andExpect(status().isBadRequest());

        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 16);
        when(currentUserService.requireUser()).thenReturn(user);
        when(healthDataContextService.getHealthContext(user, from, to, Set.of(), 0, 25, null))
            .thenThrow(new com.jllado.weightcontrol.service.BadRequestException("At least one Coach domain is required"));
        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-01")
                .param("to", "2026-08-16")
                .param("domains", ""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void contextPassesRecordPagination() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 16);
        when(currentUserService.requireUser()).thenReturn(user);
        CoachContextResponse response = new CoachContextResponse(
            "Europe/Madrid",
            OffsetDateTime.parse("2026-08-16T10:15:00+02:00"),
            from,
            to,
            LocalDate.of(2026, 8, 15),
            false,
            new CoachDataSemantics(true, true, true, true),
            new LinkedHashMap<>()
        );
        when(healthDataContextService.getHealthContext(user, from, to, Set.of(CoachDomain.RECORDS), 2, 10, null))
            .thenReturn(response);

        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-01")
                .param("to", "2026-08-16")
                .param("domains", "RECORDS")
                .param("recordsPage", "2")
                .param("recordsPageSize", "10"))
            .andExpect(status().isOk());

        verify(healthDataContextService).getHealthContext(user, from, to, Set.of(CoachDomain.RECORDS), 2, 10, null);
    }

    @Test
    void constraintsReadReturnsEditableFieldsWithoutUserMetadata() throws Exception {
        HealthConstraint constraint = constraint();
        constraint.getUser().setEmail("private@example.com");
        when(currentUserService.requireUser()).thenReturn(user);
        when(healthConstraintService.findAll(user)).thenReturn(List.of(constraint));

        mockMvc.perform(get("/api/chatgpt-actions/coach/health-constraints"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].source").value("PHYSIOTHERAPIST"))
            .andExpect(jsonPath("$[0].user").doesNotExist())
            .andExpect(jsonPath("$[0].createdAt").doesNotExist());
    }

    @Test
    void confirmedCreateWritesExactConstraint() throws Exception {
        HealthConstraint constraint = constraint();
        when(currentUserService.requireUser()).thenReturn(user);
        when(healthConstraintService.createConfirmed(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(constraint);

        mockMvc.perform(post("/api/chatgpt-actions/coach/health-constraints")
                .contentType("application/json")
                .content(constraintJson(true, "Prescribed core exercises")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.title").value("Prescribed core exercises"));
        verify(notifications).recordGptAction(user, "Health constraint saved", "/settings");

    }

    @Test
    void confirmedUpdateUsesOwnedResourceId() throws Exception {
        HealthConstraint constraint = constraint();
        when(currentUserService.requireUser()).thenReturn(user);
        when(healthConstraintService.updateConfirmed(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.eq(10L),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(constraint);

        mockMvc.perform(put("/api/chatgpt-actions/coach/health-constraints/10")
                .contentType("application/json")
                .content(constraintJson(true, "Prescribed core exercises")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("PHYSIOTHERAPIST"));
        verify(notifications).recordGptAction(user, "Health constraint updated", "/settings");

    }

    @Test
    void coachWritesRejectMissingFalseConfirmationAndInvalidValues() throws Exception {
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-constraints")
                .contentType("application/json")
                .content(constraintJson(false, "Prescribed core exercises")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-constraints")
                .contentType("application/json")
                .content(constraintJsonWithoutConfirmation()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-constraints")
                .contentType("application/json")
                .content(constraintJson(true, "")))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(healthConstraintService, notifications, events);
    }

    @Test
    void activePlanReadReturnsNoContentWhenAbsent() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(coachingPlanService.find(user)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/chatgpt-actions/coach/active-plan"))
            .andExpect(status().isNoContent());
    }

    @Test
    void confirmedActivePlanReplacementUsesCurrentUser() throws Exception {
        CoachingPlan plan = coachingPlan();
        when(currentUserService.requireUser()).thenReturn(user);
        when(coachingPlanService.replaceConfirmed(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(plan);

        mockMvc.perform(put("/api/chatgpt-actions/coach/active-plan")
                .contentType("application/json")
                .content(planJson(true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.goal").value("Improve strength consistently"))
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.user").doesNotExist());
        verify(notifications).recordGptAction(user, "Coaching plan updated", "/plan");

    }

    @Test
    void activePlanReplacementRejectsMissingFalseConfirmationAndBlankItems() throws Exception {
        mockMvc.perform(put("/api/chatgpt-actions/coach/active-plan")
                .contentType("application/json")
                .content(planJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/chatgpt-actions/coach/active-plan")
                .contentType("application/json")
                .content(planJsonWithoutConfirmation()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/chatgpt-actions/coach/active-plan")
                .contentType("application/json")
                .content(planJson(true).replace("Consistency", " ")))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(coachingPlanService);
    }

    @Test
    void nutritionReadsReturnEditableIdsForTheRequestedRange() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 19);
        LocalDate to = LocalDate.of(2026, 8, 20);
        Meal meal = meal();
        meal.setRating(8);
        FastingPeriod period = fastingPeriod();
        when(currentUserService.requireUser()).thenReturn(user);
        when(mealService.findBetween(user, from, to)).thenReturn(List.of(meal));
        when(fastingPeriodService.findBetween(user, from, to)).thenReturn(List.of(period));

        mockMvc.perform(get("/api/chatgpt-actions/coach/meals")
                .param("from", "2026-08-19")
                .param("to", "2026-08-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(30))
            .andExpect(jsonPath("$[0].source").value("GPT_IMAGE_ESTIMATE"))
            .andExpect(jsonPath("$[0].rating").value(8));
        mockMvc.perform(get("/api/chatgpt-actions/coach/fasting-periods")
                .param("from", "2026-08-19")
                .param("to", "2026-08-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(40));

        verify(healthDataContextService, org.mockito.Mockito.times(2)).validateCoachDateRange(from, to);
    }

    @Test
    void confirmedMealRatingUsesCurrentUserWithoutReplacingNutrition() throws Exception {
        var meal = meal();
        meal.setRating(8);
        when(currentUserService.requireUser()).thenReturn(user);
        when(mealService.findForUser(user, 30L)).thenReturn(meal);
        when(mealService.rateConfirmed(eq(user), eq(30L), any())).thenReturn(meal);

        mockMvc.perform(put("/api/chatgpt-actions/coach/meals/30").param("target", "RATING")
                .contentType("application/json").content("{\"rating\":8,\"confirmed\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").value(8))
            .andExpect(jsonPath("$.calories").value(700));
        verify(mealService).rateConfirmed(user, 30L, new com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRatingRequest(8, true));
        verify(notifications).recordGptAction(user, "Meal rated", "/?tab=calories&date=2026-08-20");
        verifyNoInteractions(personalRecordMutationService, fastingPeriodService);
    }

    @Test
    void mealRatingRejectsMissingConfirmationInvalidScoresAndFractionalJson() throws Exception {
        for (String body : List.of("{\"rating\":8}", "{\"rating\":8,\"confirmed\":false}",
                "{\"confirmed\":true}", "{\"rating\":null,\"confirmed\":true}",
                "{\"rating\":0,\"confirmed\":true}", "{\"rating\":11,\"confirmed\":true}",
                "{\"rating\":8.5,\"confirmed\":true}")) {
            mockMvc.perform(put("/api/chatgpt-actions/coach/meals/30").param("target", "RATING")
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(mealService, personalRecordMutationService, notifications);
    }

    @Test
    void mealRatingPreservesOwnershipFailureWithoutSuccessNotification() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(mealService.findForUser(user, 30L)).thenThrow(new com.jllado.weightcontrol.service.NotFoundException("Meal not found"));
        mockMvc.perform(put("/api/chatgpt-actions/coach/meals/30").param("target", "RATING")
                .contentType("application/json").content("{\"rating\":8,\"confirmed\":true}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(notifications);
    }

    @Test
    void coachCatalogSelectionDefaultsFalseAndAcceptsAutomaticOptIn() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.createConfirmedMeal(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any())).thenReturn(meal());
        for (boolean add : new boolean[] {false, true}) {
            String body = mealJson(true);
            if (add) body = body.replace("\"name\": \"Chicken\"", "\"name\": \"Chicken\", \"addToCatalog\": true");
            mockMvc.perform(post("/api/chatgpt-actions/coach/meals").contentType("application/json").content(body)).andExpect(status().isOk());
            verify(personalRecordMutationService).createConfirmedMeal(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.argThat(request -> request.dishes().getFirst().addToCatalog() == add));
        }
    }

    @Test
    void createMealBindsTheConfirmedRating() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        Meal saved = meal();
        saved.setRating(8);
        when(personalRecordMutationService.createConfirmedMeal(eq(user), any())).thenReturn(saved);

        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true).replace("\"confirmed\": true", "\"confirmed\": true,\n  \"rating\": 8")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").value(8));

        var request = ArgumentCaptor.forClass(com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRequest.class);
        verify(personalRecordMutationService).createConfirmedMeal(eq(user), request.capture());
        assertEquals(8, request.getValue().rating());
        verify(notifications).recordGptAction(user, "Dinner saved", "/?tab=calories&date=2026-08-20");
    }

    @Test
    void updateMealItemBindsCompleteMealAndPreservesSiblingAndMetadata() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.updateConfirmedMeal(eq(user), eq(30L), any())).thenReturn(meal());
        when(mealService.findBetween(user, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20)))
            .thenReturn(List.of(meal()));
        var mapper = new ObjectMapper();
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(mealJson(true));
        body.put("rating", 8);
        var dishes = (com.fasterxml.jackson.databind.node.ArrayNode) body.get("dishes");
        var sibling = dishes.get(0).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) sibling).put("name", "Yogurt");
        ((com.fasterxml.jackson.databind.node.ObjectNode) sibling).put("fruit", false);
        dishes.add(sibling);
        ((com.fasterxml.jackson.databind.node.ObjectNode) dishes.get(0)).put("name", "Blueberries");
        ((com.fasterxml.jackson.databind.node.ObjectNode) dishes.get(0)).put("fruit", true);

        var updateResponse = mockMvc.perform(put("/api/chatgpt-actions/coach/meals/30")
                .contentType("application/json").content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var freshRead = mockMvc.perform(get("/api/chatgpt-actions/coach/meals")
                .param("from", "2026-08-20").param("to", "2026-08-20"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(updateResponse), mapper.readTree(freshRead).get(0));

        var captured = ArgumentCaptor.forClass(com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRequest.class);
        verify(personalRecordMutationService).updateConfirmedMeal(eq(user), eq(30L), captured.capture());
        var request = captured.getValue();
        assertEquals(LocalDate.of(2026, 8, 20), request.date());
        assertEquals(MealType.DINNER, request.mealType());
        assertEquals(LocalTime.of(20, 30), request.mealTime());
        assertEquals(30, request.durationMinutes());
        assertEquals(MealSource.GPT_IMAGE_ESTIMATE, request.source());
        assertEquals(8, request.rating());
        assertEquals("Estimated dinner", request.notes());
        assertEquals(List.of("Blueberries", "Yogurt"), request.dishes().stream().map(d -> d.name()).toList());
        assertEquals(true, request.dishes().getFirst().fruit());
        assertEquals(false, request.dishes().get(1).fruit());
        verify(mealService).findBetween(user, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20));
    }

    @Test
    void createMealLogsRequestDurationAndOutcome(CapturedOutput output) throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.createConfirmedMeal(eq(user), any())).thenReturn(meal());

        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true)))
            .andExpect(status().isOk());

        assertTrue(output.getOut().matches("(?s).*coach_action_timing action=createMeal stage=request request_id=[0-9a-f-]+ elapsed_ms=\\d+ outcome=success.*"));
    }

    @Test
    void createMealLogsFailedRequestOutcome(CapturedOutput output) throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.createConfirmedMeal(eq(user), any()))
            .thenThrow(new com.jllado.weightcontrol.service.BadRequestException("Meal rejected"));

        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true)))
            .andExpect(status().isBadRequest());

        assertTrue(output.getOut().matches("(?s).*coach_action_timing action=createMeal stage=request request_id=[0-9a-f-]+ elapsed_ms=\\d+ outcome=error.*"));
    }

    @Test
    void createMealRejectsInvalidRatingsBeforeMutation() throws Exception {
        for (String value : List.of("0", "11", "8.5", "null")) {
            mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                    .contentType("application/json")
                    .content(mealJson(true).replace("\"confirmed\": true", "\"confirmed\": true,\n  \"rating\": " + value)))
                .andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/chatgpt-actions/coach/meals/30")
                    .contentType("application/json")
                    .content(mealJson(true).replace("\"confirmed\": true", "\"confirmed\": true,\n  \"rating\": " + value)))
                .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(currentUserService, personalRecordMutationService, notifications);
    }

    @Test
    void confirmedNutritionWritesUseTheCurrentUser() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(mealService.findForUser(user, 30L)).thenReturn(meal());
        when(fastingPeriodService.findForUser(user, 40L)).thenReturn(fastingPeriod());
        when(personalRecordMutationService.createConfirmedMeal(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(meal());
        when(personalRecordMutationService.createConfirmedFastingPeriod(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(new PersonalRecordMutationService.MutationResult<>(fastingPeriod(), List.of()));
        when(personalRecordMutationService.updateConfirmedMeal(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.eq(30L),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(meal());
        when(personalRecordMutationService.updateConfirmedFastingPeriod(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.eq(40L),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(fastingPeriod());

        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(30))
            .andExpect(jsonPath("$.date").value("2026-08-20"))
            .andExpect(jsonPath("$.mealType").value("DINNER"))
            .andExpect(jsonPath("$.mealSequence").value(1))
            .andExpect(jsonPath("$.mealTime").value("20:30:00"))
            .andExpect(jsonPath("$.durationMinutes").value(30))
            .andExpect(jsonPath("$.calories").value(700))
            .andExpect(jsonPath("$.proteinGrams").value(40))
            .andExpect(jsonPath("$.carbohydrateGrams").value(70))
            .andExpect(jsonPath("$.fatGrams").value(20))
            .andExpect(jsonPath("$.notes").value("Estimated dinner"))
            .andExpect(jsonPath("$.source").value("GPT_IMAGE_ESTIMATE"))
            .andExpect(jsonPath("$.dishes[0].name").value("Chicken"));
        mockMvc.perform(post("/api/chatgpt-actions/coach/fasting-periods")
                .contentType("application/json")
                .content(fastingJson(true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(40));
        mockMvc.perform(put("/api/chatgpt-actions/coach/meals/30")
                .contentType("application/json")
                .content(mealJson(true)))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/chatgpt-actions/coach/fasting-periods/40")
                .contentType("application/json")
                .content(fastingJson(true)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/chatgpt-actions/coach/meals/30/delete")
                .contentType("application/json")
                .content("{\"confirmed\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));
        mockMvc.perform(post("/api/chatgpt-actions/coach/fasting-periods/40/delete")
                .contentType("application/json")
                .content("{\"confirmed\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

        verify(personalRecordMutationService).deleteConfirmedMeal(user, 30L, true);
        verify(personalRecordMutationService).deleteConfirmedFastingPeriod(user, 40L, true);
        verify(notifications).recordGptAction(user, "Dinner saved", "/?tab=calories&date=2026-08-20");
        verify(notifications).recordGptAction(user, "Dinner updated", "/?tab=calories&date=2026-08-20");
        verify(notifications).recordGptAction(user, "Meal deleted", "/?tab=calories&date=2026-08-20");
        verify(notifications).recordGptAction(user, "Fasting period saved", "/?tab=calories&date=2026-08-19");
        verify(notifications).recordGptAction(user, "Fasting period updated", "/?tab=calories&date=2026-08-19");
        verify(notifications).recordGptAction(user, "Fasting period deleted", "/?tab=calories&date=2026-08-19");

    }

    @Test
    void nutritionWritesRejectMissingOrFalseConfirmation() throws Exception {
        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/fasting-periods")
                .contentType("application/json")
                .content(fastingJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/meals/30/delete")
                .contentType("application/json")
                .content("{\"confirmed\":false}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(mealService, personalRecordMutationService, fastingPeriodService);
    }

    @Test
    void healthEntryWritesRejectFalseConfirmationBeforeCallingServices() throws Exception {
        mockMvc.perform(post("/api/chatgpt-actions/coach/weights").contentType("application/json").content(weightJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/blood-pressures").contentType("application/json").content(bloodPressureJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/moods").contentType("application/json").content(moodJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/sleeps").contentType("application/json").content(sleepJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/back-pain-episodes").contentType("application/json").content(backPainJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/sicknesses").contentType("application/json").content(sicknessJson(false)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/lipid-panels").contentType("application/json").content(lipidPanelJson(false)))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(personalRecordMutationService, backPainEpisodeService, sicknessService);
    }

    @Test
    void genericWeightWriteRejectsMissingBodyCompositionBeforePersistence() throws Exception {
        String payload = weightJson(true).replace(",\"fatPercentage\":20.0,\"muscle\":60.0", "");

        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/WEIGHT")
                .contentType("application/json").content(payload))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(personalRecordMutationService, weightService);
    }

    @Test
    void genericWeightWriteAcceptsLocalDateAndPreservesOffsetTimestamps() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.createWeight(eq(user), any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(1, WeightRequest.class);
            var saved = new Weight();
            saved.setId(1L);
            saved.setMeasuredAt(request.date());
            return new MutationResult<>(saved, List.of());
        });

        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/WEIGHT")
                .contentType("application/json")
                .content(weightJson(true).replace("2026-08-20T08:00:00+02:00", "2026-08-20")))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/WEIGHT")
                .contentType("application/json")
                .content(weightJson(true).replace("2026-08-20T", "2026-08-20t")))
            .andExpect(status().isOk());

        var captured = ArgumentCaptor.forClass(WeightRequest.class);
        verify(personalRecordMutationService, org.mockito.Mockito.times(2)).createWeight(eq(user), captured.capture());
        assertEquals(DateTimes.startOfDay(LocalDate.of(2026, 8, 20)), captured.getAllValues().get(0).date());
        assertEquals(OffsetDateTime.parse("2026-08-20t08:00:00+02:00"), captured.getAllValues().get(1).date());

        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/WEIGHT")
                .contentType("application/json")
                .content(weightJson(true).replace("2026-08-20T08:00:00+02:00", "2026-02-30")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/health-entries/WEIGHT")
                .contentType("application/json")
                .content(weightJson(false).replace("2026-08-20T08:00:00+02:00", "2026-08-20")))
            .andExpect(status().isBadRequest());
        verify(personalRecordMutationService, org.mockito.Mockito.times(2)).createWeight(eq(user), any());
    }

    @Test
    void painFreeCoachWritesRequireConfirmationAndReturnNoLocation() throws Exception {
        String payload = """
            {"date":"2026-08-20","period":"MORNING","region":null,"side":null,"severity":"NONE","confirmed":false}
            """;
        mockMvc.perform(post("/api/chatgpt-actions/coach/back-pain-episodes").contentType("application/json").content(payload))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/chatgpt-actions/coach/back-pain-episodes/9").contentType("application/json").content(payload.replace("\"date\":\"2026-08-20\",", "")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(backPainEpisodeService);

        when(currentUserService.requireUser()).thenReturn(user);
        BackPainEpisode saved = new BackPainEpisode();
        saved.setId(9L);
        saved.setEpisodeDate(LocalDate.of(2026, 8, 20));
        saved.setPeriod(MoodPeriod.MORNING);
        saved.setSeverity(BackPainSeverity.NONE);
        when(backPainEpisodeService.create(eq(user), any())).thenReturn(saved);
        when(backPainEpisodeService.update(eq(user), eq(9L), any())).thenReturn(saved);
        when(backPainEpisodeService.requireOwned(user, 9L)).thenReturn(saved);
        mockMvc.perform(post("/api/chatgpt-actions/coach/back-pain-episodes").contentType("application/json").content(payload.replace("false", "true")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.severity").value("NONE"))
            .andExpect(jsonPath("$.region").isEmpty())
            .andExpect(jsonPath("$.side").isEmpty());
        mockMvc.perform(put("/api/chatgpt-actions/coach/back-pain-episodes/9").contentType("application/json").content(payload.replace("false", "true").replace("\"date\":\"2026-08-20\",", "")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.severity").value("NONE"));
    }

    @Test
    void directSleepWritePreservesScreenshotDurationsInSeconds() throws Exception {
        Sleep savedSleep = new Sleep();
        savedSleep.setId(8L);
        savedSleep.setSleepDate(LocalDate.of(2026, 8, 30));
        savedSleep.setBedtimeStart(OffsetDateTime.parse("2026-08-29T22:49:00+02:00"));
        savedSleep.setBedtimeEnd(OffsetDateTime.parse("2026-08-30T06:27:00+02:00"));
        savedSleep.setTotalSleepDuration(19080);
        savedSleep.setDeepSleepDuration(4800);
        savedSleep.setRemSleepDuration(4020);
        savedSleep.setLightSleepDuration(9660);
        savedSleep.setAwakeTime(5160);
        savedSleep.setAverageHeartRate(new BigDecimal("61.00"));
        savedSleep.setAverageHrv(26);
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.createSleep(eq(user), any()))
            .thenReturn(new PersonalRecordMutationService.MutationResult<>(savedSleep, List.of()));

        mockMvc.perform(post("/api/chatgpt-actions/coach/sleeps")
                .contentType("application/json")
                .content("{\"sleepDate\":\"2026-08-30\",\"bedtimeStart\":\"2026-08-29T22:49:00+02:00\",\"bedtimeEnd\":\"2026-08-30T06:27:00+02:00\",\"totalSleepDuration\":19080,\"deepSleepDuration\":4800,\"remSleepDuration\":4020,\"lightSleepDuration\":9660,\"awakeTime\":5160,\"averageHeartRate\":61.0,\"averageHrv\":26,\"confirmed\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSleepDuration").value(19080));

        ArgumentCaptor<SleepRequest> requestCaptor = ArgumentCaptor.forClass(SleepRequest.class);
        verify(personalRecordMutationService).createSleep(eq(user), requestCaptor.capture());
        assertEquals(19080, requestCaptor.getValue().totalSleepDuration());
        assertEquals(4800, requestCaptor.getValue().deepSleepDuration());
        assertEquals(4020, requestCaptor.getValue().remSleepDuration());
        assertEquals(9660, requestCaptor.getValue().lightSleepDuration());
        assertEquals(5160, requestCaptor.getValue().awakeTime());
        verify(notifications).recordGptAction(user, "Sleep saved", "/?tab=sleep&date=2026-08-30");

    }

    @Test
    void healthEntryListsUseTheRequestedRangeAndReturnEditableArrays() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 19);
        LocalDate to = LocalDate.of(2026, 8, 20);
        when(currentUserService.requireUser()).thenReturn(user);
        when(weightService.findBetween(user, from, to)).thenReturn(List.of());
        when(bloodPressureService.findBetween(user, from, to)).thenReturn(List.of());
        when(moodService.findBetween(user, from, to)).thenReturn(List.of());
        when(sleepService.findBetween(user, from, to)).thenReturn(List.of());
        when(backPainEpisodeService.findBetween(user, from, to)).thenReturn(List.of());
        when(sicknessService.findBetween(user, from, to)).thenReturn(List.of());
        when(lipidPanelService.findBetween(user, from, to)).thenReturn(List.of());

        for (String path : List.of("weights", "blood-pressures", "moods", "sleeps", "back-pain-episodes", "sicknesses", "lipid-panels")) {
            mockMvc.perform(get("/api/chatgpt-actions/coach/" + path).param("from", "2026-08-19").param("to", "2026-08-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        verify(healthDataContextService, org.mockito.Mockito.times(7)).validateCoachDateRange(from, to);
    }

    @Test
    void imageEstimateCreationRejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true).replace("  \"date\": \"2026-08-20\",\n", "")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true).replace("  \"mealType\": \"DINNER\",\n", "")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true).replace("  \"mealTime\": \"20:30:00\",\n", "")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true).replace("  \"calories\": 700,\n", "")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true).replace("  \"source\": \"GPT_IMAGE_ESTIMATE\",\n", "")))
            .andExpect(status().isBadRequest());

        for (String field : List.of("proteinGrams", "carbohydrateGrams", "fatGrams")) {
            mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                    .contentType("application/json")
                    .content(mealJson(true).replace("\"" + field + "\": " + dishMacroValue(field) + (field.equals("fatGrams") ? "\n" : ",\n"), "")))
                .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(personalRecordMutationService);
    }

    @Test
    void imageEstimateCreationRejectsImageDataFields() throws Exception {
        for (String field : List.of("imageBytes", "imageFileId", "imageUrl")) {
            mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                    .contentType("application/json")
                    .content(mealJson(true).replace("\n}", ",\n  \"" + field + "\": \"not accepted\"\n}")))
                .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(personalRecordMutationService);
    }

    @Test
    void workoutAssessmentActionsUseTheCurrentUserAndDate() throws Exception {
        LocalDate workoutDate = LocalDate.of(2026, 8, 20);
        Instant timestamp = Instant.parse("2026-08-20T18:30:00Z");
        WorkoutAssessmentResponse response = new WorkoutAssessmentResponse(
            8,
            7,
            "Strong alignment with the current strength goal.",
            "Consistent compound work.",
            "Add one pulling set.",
            "Repeat with controlled progression.",
            "Improve strength consistently",
            timestamp,
            timestamp
        );
        when(currentUserService.requireUser()).thenReturn(user);
        when(workoutAssessmentService.save(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.eq(workoutDate),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(response);

        mockMvc.perform(get("/api/chatgpt-actions/coach/workouts/2026-08-20/assessment-context"))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/chatgpt-actions/coach/workouts/2026-08-20/assessment")
                .contentType("application/json")
                .content(assessmentJson(true, 8)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.goalAlignmentScore").value(8))
            .andExpect(jsonPath("$.goalSnapshot").value("Improve strength consistently"))
            .andExpect(jsonPath("$.workoutId").doesNotExist())
            .andExpect(jsonPath("$.user").doesNotExist());

        verify(workoutAssessmentService).getContext(user, workoutDate, null);
        verify(notifications).recordGptAction(user, "Workout assessment saved", "/?tab=workout&date=2026-08-20");

    }

    @Test
    void assessmentContextPublishesDatedRestEvidenceWithoutInternalPlanIdentifiers() throws Exception {
        var date = LocalDate.of(2026, 8, 20);
        var version = Instant.parse("2026-08-19T10:00:00Z");
        var weekly = new AssessmentWeeklyPlanData("AVAILABLE", date.minusDays(3), date.plusDays(10), version,
            new AssessmentPlannedDayData(date.getDayOfWeek(), true, "Recovery", List.of()));
        var response = new WorkoutAssessmentContextResponse(
            new AssessmentDayData(date, List.of()), weekly, null, List.of(), List.of(), null, version, "opaque-context");
        when(currentUserService.requireUser()).thenReturn(user);
        when(workoutAssessmentService.getContext(user, date, null)).thenReturn(response);
        mockMvc.perform(get("/api/chatgpt-actions/coach/workouts/2026-08-20/assessment-context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.weeklyPlan.evidenceStatus").value("AVAILABLE"))
            .andExpect(jsonPath("$.weeklyPlan.day.rest").value(true))
            .andExpect(jsonPath("$.weeklyPlan.day.sessions").isEmpty())
            .andExpect(jsonPath("$.weeklyPlan.id").doesNotExist())
            .andExpect(jsonPath("$.weeklyPlan.updateToken").doesNotExist())
            .andExpect(jsonPath("$.workoutContextToken").value("opaque-context"));
    }

    @Test
    void legacySessionReferencesAreRejectedWithReloadInstructions() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(currentUserService.requireUser()).thenReturn(user);
        when(workoutAssessmentService.getContext(user, date, "session-a")).thenThrow(new com.jllado.weightcontrol.service.BadRequestException("Assessments now cover the whole day; reload context by date without sessionReference"));
        mockMvc.perform(get("/api/chatgpt-actions/coach/workouts/2026-08-20/assessment-context?sessionReference=session-a"))
            .andExpect(status().isBadRequest());
        when(workoutAssessmentService.save(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq(date), org.mockito.ArgumentMatchers.eq("session-a"), org.mockito.ArgumentMatchers.any())).thenThrow(new com.jllado.weightcontrol.service.BadRequestException("Reload daily context"));
        mockMvc.perform(put("/api/chatgpt-actions/coach/workouts/2026-08-20/assessment?sessionReference=session-a").contentType("application/json").content(assessmentJson(true, 8))).andExpect(status().isBadRequest());
    }

    @Test
    void workoutAssessmentWriteRejectsInvalidScoresAndConfirmationBeforeCallingTheService() throws Exception {
        mockMvc.perform(put("/api/chatgpt-actions/coach/workouts/2026-08-20/assessment")
                .contentType("application/json")
                .content(assessmentJson(false, 8)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/chatgpt-actions/coach/workouts/2026-08-20/assessment")
                .contentType("application/json")
                .content(assessmentJson(true, 11)))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(workoutAssessmentService);
    }

    @Test
    void progressPhotoActionsReturnPrivateMetadataAndSignedFileResponses() throws Exception {
        ProgressPhotoSetResponse photoSet = new ProgressPhotoSetResponse(
            50L,
            LocalDate.of(2026, 8, 20),
            new java.math.BigDecimal("80.00"),
            new java.math.BigDecimal("20.00"),
            new java.math.BigDecimal("16.00"),
            new java.math.BigDecimal("60.00"),
            new java.math.BigDecimal("75.00"),
            List.of(ProgressPhotoSide.FRONT, ProgressPhotoSide.LEFT)
        );
        when(currentUserService.requireUser()).thenReturn(user);
        when(progressPhotoService.findAll(user)).thenReturn(List.of(photoSet));
        when(progressPhotoService.getFiles(user, 50L, Set.of(ProgressPhotoSide.FRONT, ProgressPhotoSide.LEFT)))
            .thenReturn(new OpenAiFileResponse(List.of("https://weightcontrol.test/api/chatgpt-files/progress-photos/token")));

        mockMvc.perform(get("/api/chatgpt-actions/coach/progress-photos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].photoSetId").value(50))
            .andExpect(jsonPath("$[0].availableSides[0]").value("FRONT"))
            .andExpect(jsonPath("$[0].photoFrontPath").doesNotExist())
            .andExpect(jsonPath("$[0].user").doesNotExist());
        mockMvc.perform(get("/api/chatgpt-actions/coach/progress-photos/50/files")
                .param("sides", "FRONT,LEFT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openaiFileResponse[0]").value("https://weightcontrol.test/api/chatgpt-files/progress-photos/token"));
    }

    private String assessmentJson(boolean confirmed, int goalAlignmentScore) {
        return """
            {
              "goalAlignmentScore": %d,
              "estimatedTrainingDemandScore": 7,
              "rationale": "Strong alignment with the current strength goal.",
              "strength": "Consistent compound work.",
              "improvement": "Add one pulling set.",
              "nextWorkoutAction": "Repeat with controlled progression.",
              "planUpdatedAt": "2026-08-20T18:30:00Z",
              "workoutContextToken": "daily-context-token",
              "confirmed": %s
            }
            """.formatted(goalAlignmentScore, confirmed);
    }

    private String weightJson(boolean confirmed) {
        return "{\"date\":\"2026-08-20T08:00:00+02:00\",\"weight\":80.0,\"fatPercentage\":20.0,\"muscle\":60.0,\"confirmed\":" + confirmed + "}";
    }

    private String bloodPressureJson(boolean confirmed) {
        return "{\"date\":\"2026-08-20T08:00:00+02:00\",\"upper\":120,\"lower\":80,\"confirmed\":" + confirmed + "}";
    }

    private String moodJson(boolean confirmed) {
        return "{\"date\":\"2026-08-20\",\"period\":\"MORNING\",\"value\":4,\"confirmed\":" + confirmed + "}";
    }

    private String sleepJson(boolean confirmed) {
        return "{\"sleepDate\":\"2026-08-20\",\"bedtimeStart\":\"2026-08-19T23:00:00+02:00\",\"bedtimeEnd\":\"2026-08-20T07:00:00+02:00\",\"totalSleepDuration\":25200,\"deepSleepDuration\":5400,\"remSleepDuration\":5400,\"lightSleepDuration\":14400,\"awakeTime\":3600,\"averageHeartRate\":55.0,\"averageHrv\":50,\"confirmed\":" + confirmed + "}";
    }

    private String backPainJson(boolean confirmed) {
        return "{\"date\":\"2026-08-20\",\"period\":\"MORNING\",\"region\":\"LOWER\",\"side\":\"LEFT\",\"severity\":\"MILD\",\"confirmed\":" + confirmed + "}";
    }

    private String sicknessJson(boolean confirmed) {
        return "{\"date\":\"2026-08-20\",\"type\":\"COLD\",\"severity\":\"LOW\",\"confirmed\":" + confirmed + "}";
    }

    private String lipidPanelJson(boolean confirmed) {
        return "{\"date\":\"2026-08-20\",\"totalCholesterol\":180,\"hdlCholesterol\":60,\"ldlCholesterol\":100,\"triglycerides\":100,\"confirmed\":" + confirmed + "}";
    }

    private String constraintJson(boolean confirmed, String title) {
        return """
            {
              "type": "CLINICIAN_GUIDANCE",
              "title": "%s",
              "details": "Bird dogs and side planks three times per week",
              "source": "PHYSIOTHERAPIST",
              "startDate": "2026-08-01",
              "endDate": null,
              "active": true,
              "confirmed": %s
            }
            """.formatted(title, confirmed);
    }

    private String constraintJsonWithoutConfirmation() {
        return """
            {
              "type": "CLINICIAN_GUIDANCE",
              "title": "Prescribed core exercises",
              "details": "Bird dogs and side planks three times per week",
              "source": "PHYSIOTHERAPIST",
              "startDate": "2026-08-01",
              "endDate": null,
              "active": true
            }
            """;
    }

    private HealthConstraint constraint() {
        User owner = new User();
        owner.setId(1L);
        HealthConstraint constraint = new HealthConstraint();
        constraint.setId(10L);
        constraint.setUser(owner);
        constraint.setType(HealthConstraintType.CLINICIAN_GUIDANCE);
        constraint.setTitle("Prescribed core exercises");
        constraint.setDetails("Bird dogs and side planks three times per week");
        constraint.setSource(HealthConstraintSource.PHYSIOTHERAPIST);
        constraint.setStartDate(LocalDate.of(2026, 8, 1));
        constraint.setActive(true);
        return constraint;
    }

    private String planJson(boolean confirmed) {
        return """
            {
              "goal": "Improve strength consistently",
              "principles": ["Train without aggravating pain"],
              "priorities": ["Consistency", "Recovery"],
              "actions": ["Complete three strength sessions"],
              "startDate": "2026-08-10",
              "reviewDate": "2026-09-10",
              "notes": "Review training tolerance",
              "confirmed": %s
            }
            """.formatted(confirmed);
    }

    private String planJsonWithoutConfirmation() {
        return planJson(true).replace(",\n  \"confirmed\": true", "");
    }

    private CoachingPlan coachingPlan() {
        CoachingPlan plan = new CoachingPlan();
        plan.setId(20L);
        plan.setUser(user);
        plan.setGoal("Improve strength consistently");
        plan.setPrinciples(List.of("Train without aggravating pain"));
        plan.setPriorities(List.of("Consistency", "Recovery"));
        plan.setActions(List.of("Complete three strength sessions"));
        plan.setStartDate(LocalDate.of(2026, 8, 10));
        plan.setReviewDate(LocalDate.of(2026, 9, 10));
        plan.setNotes("Review training tolerance");
        plan.setUpdatedAt(java.time.Instant.parse("2026-08-15T10:00:00Z"));
        return plan;
    }

    @Test
    void mealWritesDefaultOmittedDurationAndRejectInvalidDurations() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.createConfirmedMeal(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any())).thenReturn(meal());

        mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                .contentType("application/json")
                .content(mealJson(true).replace("  \"durationMinutes\": 30,\n", "")))
            .andExpect(status().isOk());
        verify(personalRecordMutationService).createConfirmedMeal(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.argThat(request -> request.durationMinutes() == 30));

        for (String duration : List.of("0", "-1", "30.5")) {
            mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                    .contentType("application/json")
                    .content(mealJson(true).replace("\"durationMinutes\": 30", "\"durationMinutes\": " + duration)))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void coachMealRequiresNonemptyFoodBreakdown() throws Exception {
        for (String body : List.of(
                mealJson(true).replaceAll("(?s)\\s*\"dishes\": \\[.*?\\],\\n", ""),
                mealJson(true).replaceAll("(?s)\"dishes\": \\[.*?\\]", "\"dishes\": []"))) {
            mockMvc.perform(post("/api/chatgpt-actions/coach/meals")
                    .contentType("application/json")
                    .content(body))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(personalRecordMutationService);
    }

    private String mealJson(boolean confirmed) {
        return """
            {
              "date": "2026-08-20",
              "mealType": "DINNER",
              "mealTime": "20:30:00",
              "durationMinutes": 30,
              "calories": 700,
              "proteinGrams": 40,
              "carbohydrateGrams": 70,
              "fatGrams": 20,
              "notes": "Estimated dinner",
              "dishes": [{
                "name": "Chicken", "vitaminDMicrograms":0,"omega3Milligrams":1,"magnesiumMilligrams":10,"nutrientSource":"Test composition","nutrientsEstimated":false,
                "calories": 700,
                "proteinGrams": 40,
                "carbohydrateGrams": 70,
                "fatGrams": 20
              }],
              "source": "GPT_IMAGE_ESTIMATE",
              "confirmed": %s
            }
            """.formatted(confirmed);
    }

    private String coachRecipeJson(boolean confirmed) {
        return """
            {"confirmed":%s,"recipe":{"name":"Fruit bowl","servings":2.5,"ingredients":[{
              "name":"Blueberries","calories":45,"proteinGrams":1.00,"carbohydrateGrams":9.00,"fatGrams":0.50,
              "quantity":50,"unit":"GRAM","fruit":true,"reference":{"quantity":100,"calories":90,
                "proteinGrams":2.00,"carbohydrateGrams":18.00,"fatGrams":1.00,
                "vitaminDMicrograms":0.10,"omega3Milligrams":5.00,"magnesiumMilligrams":10.00},
              "vitaminDMicrograms":0.05,"omega3Milligrams":2.50,"magnesiumMilligrams":5.00,
              "nutrientSource":"Saved composition","nutrientsEstimated":false
            },{
              "name":"Yogurt","calories":60,"proteinGrams":4.00,"carbohydrateGrams":5.00,"fatGrams":2.00,
              "quantity":100,"unit":"GRAM","fruit":false,"reference":{"quantity":100,"calories":60,
                "proteinGrams":4.00,"carbohydrateGrams":5.00,"fatGrams":2.00,
                "vitaminDMicrograms":0.20,"omega3Milligrams":0.10,"magnesiumMilligrams":11.00},
              "vitaminDMicrograms":0.20,"omega3Milligrams":0.10,"magnesiumMilligrams":11.00,
              "nutrientSource":"Yogurt label","nutrientsEstimated":false
            }]}}
            """.formatted(confirmed);
    }

    private RecipeResponse savedRecipe() {
        var reference = new DishReference(new BigDecimal("100"), 90, new BigDecimal("2.00"),
            new BigDecimal("18.00"), new BigDecimal("1.00"), new BigDecimal("0.10"),
            new BigDecimal("5.00"), new BigDecimal("10.00"));
        var ingredient = new MealDishRequest("Blueberries", 45, new BigDecimal("1.00"),
            new BigDecimal("9.00"), new BigDecimal("0.50"), new BigDecimal("50"), DishUnit.GRAM,
            reference, true, new BigDecimal("0.05"), new BigDecimal("2.50"),
            new BigDecimal("5.00"), "Saved composition", false);
        var yogurtReference = new DishReference(new BigDecimal("100"), 60, new BigDecimal("4.00"),
            new BigDecimal("5.00"), new BigDecimal("2.00"), new BigDecimal("0.20"),
            new BigDecimal("0.10"), new BigDecimal("11.00"));
        var yogurt = new MealDishRequest("Yogurt", 60, new BigDecimal("4.00"),
            new BigDecimal("5.00"), new BigDecimal("2.00"), new BigDecimal("100"), DishUnit.GRAM,
            yogurtReference, false, new BigDecimal("0.20"), new BigDecimal("0.10"),
            new BigDecimal("11.00"), "Yogurt label", false);
        return new RecipeResponse(41L, "Fruit bowl", new BigDecimal("2.5"), List.of(ingredient, yogurt));
    }

    private int dishMacroValue(String field) {
        return switch (field) {
            case "proteinGrams" -> 40;
            case "carbohydrateGrams" -> 70;
            case "fatGrams" -> 20;
            default -> throw new IllegalArgumentException(field);
        };
    }

    private String fastingJson(boolean confirmed) {
        return """
            {
              "startTime": "2026-08-19T20:00:00+02:00",
              "endTime": "2026-08-20T12:00:00+02:00",
              "notes": "Overnight fast",
              "confirmed": %s
            }
            """.formatted(confirmed);
    }

    private Meal meal() {
        Meal meal = new Meal();
        meal.setId(30L);
        meal.setUser(user);
        meal.setMealDate(LocalDate.of(2026, 8, 20));
        meal.setMealType(MealType.DINNER);
        meal.setMealSequence(1);
        meal.setMealTime(LocalTime.of(20, 30));
        meal.setDurationMinutes(30);
        meal.setCalories(700);
        meal.setProteinGrams(new BigDecimal("40"));
        meal.setCarbohydrateGrams(new BigDecimal("70"));
        meal.setFatGrams(new BigDecimal("20"));
        meal.setNotes("Estimated dinner");
        meal.setSource(MealSource.GPT_IMAGE_ESTIMATE);
        MealDish dish = new MealDish();
        dish.setId(31L);
        dish.setPosition(1);
        dish.setName("Chicken");
        dish.setCalories(700);
        dish.setProteinGrams(new BigDecimal("40"));
        dish.setCarbohydrateGrams(new BigDecimal("70"));
        dish.setFatGrams(new BigDecimal("20"));
        meal.getDishes().add(dish);
        return meal;
    }

    private FastingPeriod fastingPeriod() {
        FastingPeriod period = new FastingPeriod();
        period.setId(40L);
        period.setUser(user);
        period.setStartTime(OffsetDateTime.parse("2026-08-19T20:00:00+02:00"));
        period.setEndTime(OffsetDateTime.parse("2026-08-20T12:00:00+02:00"));
        period.setNotes("Overnight fast");
        return period;
    }
}
