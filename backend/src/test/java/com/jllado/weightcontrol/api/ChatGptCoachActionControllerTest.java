package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachCatalogResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachContextResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachDataSemantics;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.HealthConstraintSource;
import com.jllado.weightcontrol.domain.HealthConstraintType;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.BadRequestException;
import com.jllado.weightcontrol.service.CoachingPlanService;
import com.jllado.weightcontrol.service.FastingPeriodService;
import com.jllado.weightcontrol.service.HealthDataContextService;
import com.jllado.weightcontrol.service.HealthConstraintService;
import com.jllado.weightcontrol.service.MealService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private FastingPeriodService fastingPeriodService;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ChatGptCoachActionController controller = new ChatGptCoachActionController(
            healthDataContextService,
            healthConstraintService,
            coachingPlanService,
            mealService,
            fastingPeriodService,
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
            new CoachDataSemantics(true, true, true),
            new LinkedHashMap<>()
        );
        when(healthDataContextService.getHealthContext(
            user,
            from,
            to,
            Set.of(CoachDomain.BODY, CoachDomain.TRAINING)
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
            Set.of(CoachDomain.BODY, CoachDomain.TRAINING)
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
        when(healthDataContextService.getHealthContext(user, from, to, Set.of()))
            .thenThrow(new BadRequestException("At least one Coach domain is required"));
        mockMvc.perform(get("/api/chatgpt-actions/coach/context")
                .param("from", "2026-08-01")
                .param("to", "2026-08-16")
                .param("domains", ""))
            .andExpect(status().isBadRequest());
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
        when(mealService.createConfirmed(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(meal());
        when(fastingPeriodService.createConfirmed(
            org.mockito.ArgumentMatchers.eq(user),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(fastingPeriod());
        when(mealService.updateConfirmed(
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
            .andExpect(jsonPath("$.id").value(30));
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
        mockMvc.perform(delete("/api/chatgpt-actions/coach/meals/30")
                .contentType("application/json")
                .content("{\"confirmed\":true}"))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/chatgpt-actions/coach/fasting-periods/40")
                .contentType("application/json")
                .content("{\"confirmed\":true}"))
            .andExpect(status().isOk());

        verify(mealService).deleteConfirmed(user, 30L, true);
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
        mockMvc.perform(delete("/api/chatgpt-actions/coach/meals/30")
                .contentType("application/json")
                .content("{\"confirmed\":false}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(mealService, fastingPeriodService);
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
        meal.setCalories(700);
        meal.setSource(MealSource.GPT_IMAGE_ESTIMATE);
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
