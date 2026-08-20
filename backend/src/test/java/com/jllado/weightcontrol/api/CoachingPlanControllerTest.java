package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CoachingPlanService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CoachingPlanControllerTest {

    @Mock
    private CoachingPlanService service;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CoachingPlanController(service, currentUserService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(
                new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            ))
            .build();
        user = new User();
        user.setId(1L);
    }

    @Test
    void readReturnsNoContentWhenTheUserHasNoPlan() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(service.find(user)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/coaching-plan"))
            .andExpect(status().isNoContent());
    }

    @Test
    void readReturnsTheCompletePlanWithoutPersistenceMetadata() throws Exception {
        CoachingPlan plan = plan();
        when(currentUserService.requireUser()).thenReturn(user);
        when(service.find(user)).thenReturn(Optional.of(plan));

        mockMvc.perform(get("/api/coaching-plan"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.goal").value("Improve strength consistently"))
            .andExpect(jsonPath("$.principles[0]").value("Train without aggravating pain"))
            .andExpect(jsonPath("$.priorities[1]").value("Recovery"))
            .andExpect(jsonPath("$.actions[0]").value("Complete three strength sessions"))
            .andExpect(jsonPath("$.updatedAt").value("2026-08-15T10:00:00Z"))
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.user").doesNotExist())
            .andExpect(jsonPath("$.createdAt").doesNotExist());
    }

    @Test
    void replacementUsesTheCurrentUser() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(service.replace(eq(user), any())).thenReturn(plan());

        mockMvc.perform(put("/api/coaching-plan")
                .contentType("application/json")
                .content(planJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.goal").value("Improve strength consistently"));
    }

    @Test
    void replacementRejectsMissingGoalListsAndStartDate() throws Exception {
        mockMvc.perform(put("/api/coaching-plan")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private String planJson() {
        return """
            {
              "goal": "Improve strength consistently",
              "principles": ["Train without aggravating pain"],
              "priorities": ["Consistency", "Recovery"],
              "actions": ["Complete three strength sessions"],
              "startDate": "2026-08-10",
              "reviewDate": "2026-09-10",
              "notes": "Review training tolerance"
            }
            """;
    }

    private CoachingPlan plan() {
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
        plan.setUpdatedAt(Instant.parse("2026-08-15T10:00:00Z"));
        return plan;
    }
}
