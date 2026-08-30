package com.jllado.weightcontrol.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachCatalogResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachContextResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachDataSemantics;
import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.OpenAiFileResponse;
import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.ProgressPhotoSetResponse;
import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.HealthConstraintSource;
import com.jllado.weightcontrol.domain.HealthConstraintType;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealDish;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.ProgressPhotoSide;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.BadRequestException;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.service.CoachingPlanService;
import com.jllado.weightcontrol.service.FastingPeriodService;
import com.jllado.weightcontrol.service.HealthDataContextService;
import com.jllado.weightcontrol.service.HealthConstraintService;
import com.jllado.weightcontrol.service.MealService;
import com.jllado.weightcontrol.service.ProgressPhotoService;
import com.jllado.weightcontrol.service.WorkoutAssessmentService;
import com.jllado.weightcontrol.service.BackPainEpisodeService;
import com.jllado.weightcontrol.service.BloodPressureService;
import com.jllado.weightcontrol.service.LipidPanelService;
import com.jllado.weightcontrol.service.MoodService;
import com.jllado.weightcontrol.service.SicknessService;
import com.jllado.weightcontrol.service.SleepService;
import com.jllado.weightcontrol.service.WeightService;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
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
    private PersonalRecordMutationService personalRecordMutationService;
    @Mock
    private FastingPeriodService fastingPeriodService;
    @Mock
    private WorkoutAssessmentService workoutAssessmentService;
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

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ChatGptCoachActionController controller = new ChatGptCoachActionController(
            healthDataContextService,
            healthConstraintService,
            coachingPlanService,
            mealService,
            personalRecordMutationService,
            fastingPeriodService,
            workoutAssessmentService,
            progressPhotoService,
            weightService,
            bloodPressureService,
            moodService,
            sleepService,
            backPainEpisodeService,
            sicknessService,
            lipidPanelService,
            objectMapper,
            currentUserService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
        user = new User();
        user.setId(1L);
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

    @Test
    void contextParsesCommaSeparatedDomains() throws Exception {
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
            Set.of(CoachDomain.BODY, CoachDomain.TRAINING),
            0,
            25
        )).thenReturn(response);

        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-01")
                .param("to", "2026-08-16")
                .param("domains", "BODY,TRAINING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endDateComplete").value(false));

        verify(healthDataContextService).getHealthContext(
            user,
            from,
            to,
            Set.of(CoachDomain.BODY, CoachDomain.TRAINING),
            0,
            25
        );
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
        when(healthDataContextService.getHealthContext(user, from, to, Set.of(), 0, 25))
            .thenThrow(new BadRequestException("At least one Coach domain is required"));
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
        when(healthDataContextService.getHealthContext(user, from, to, Set.of(CoachDomain.RECORDS), 2, 10))
            .thenReturn(response);

        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-01")
                .param("to", "2026-08-16")
                .param("domains", "RECORDS")
                .param("recordsPage", "2")
                .param("recordsPageSize", "10"))
            .andExpect(status().isOk());

        verify(healthDataContextService).getHealthContext(user, from, to, Set.of(CoachDomain.RECORDS), 2, 10);
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

        verifyNoInteractions(healthConstraintService);
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
        FastingPeriod period = fastingPeriod();
        when(currentUserService.requireUser()).thenReturn(user);
        when(mealService.findBetween(user, from, to)).thenReturn(List.of(meal));
        when(fastingPeriodService.findBetween(user, from, to)).thenReturn(List.of(period));

        mockMvc.perform(get("/api/chatgpt-actions/coach/meals")
                .param("from", "2026-08-19")
                .param("to", "2026-08-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(30))
            .andExpect(jsonPath("$[0].source").value("GPT_IMAGE_ESTIMATE"));
        mockMvc.perform(get("/api/chatgpt-actions/coach/fasting-periods")
                .param("from", "2026-08-19")
                .param("to", "2026-08-20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(40));

        verify(healthDataContextService, org.mockito.Mockito.times(2)).validateCoachDateRange(from, to);
    }

    @Test
    void confirmedNutritionWritesUseTheCurrentUser() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(personalRecordMutationService.createConfirmedMeal(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(meal());
        when(fastingPeriodService.createConfirmed(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(fastingPeriod());
        when(personalRecordMutationService.updateConfirmedMeal(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.eq(30L),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(meal());
        when(fastingPeriodService.updateConfirmed(
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
        verify(fastingPeriodService).deleteConfirmed(user, 40L, true);
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

        verify(workoutAssessmentService).getContext(user, workoutDate);
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
              "workoutUpdatedAt": "2026-08-20T18:30:00Z",
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

    private String mealJson(boolean confirmed) {
        return """
            {
              "date": "2026-08-20",
              "mealType": "DINNER",
              "mealTime": "20:30:00",
              "calories": 700,
              "proteinGrams": 40,
              "carbohydrateGrams": 70,
              "fatGrams": 20,
              "notes": "Estimated dinner",
              "source": "GPT_IMAGE_ESTIMATE",
              "confirmed": %s
            }
            """.formatted(confirmed);
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
