package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jllado.weightcontrol.config.SecurityConfig;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.JwtSessionService;
import com.jllado.weightcontrol.security.SessionCookieService;
import com.jllado.weightcontrol.service.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {WorkoutPlanController.class, ChatGptWorkoutPlanActionController.class}, properties = {"app.chat-gpt-actions.token=plan-test-token", "app.chat-gpt-actions.user-email=plan@example.com"})
@Import(SecurityConfig.class)
class WorkoutPlanControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean WorkoutPlanService plans;
    @MockitoBean GptActionNotificationService notifications;
    @MockitoBean com.jllado.weightcontrol.security.CurrentUserService currentUser;
    @MockitoBean JwtSessionService sessions;
    @MockitoBean SessionCookieService cookies;
    @MockitoBean UserRepository users;
    @MockitoBean CoachAuthAlertService alerts;

    @Test void requiresAuthenticationAndValidatesRequests() throws Exception {
        mvc.perform(get("/api/workout-plans/current")).andExpect(status().isForbidden());
        mvc.perform(get("/api/chatgpt-actions/coach/active-plan?target=WORKOUT").with(user("owner"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/workout-plans").with(user("owner")).contentType("application/json").content("{\"days\":[]}")).andExpect(status().isBadRequest());
        when(users.findByEmail("plan@example.com")).thenReturn(Optional.of(new com.jllado.weightcontrol.domain.User()));
        mvc.perform(put("/api/chatgpt-actions/coach/active-plan?target=WORKOUT").header("Authorization", "Bearer plan-test-token").contentType("application/json").content("{\"confirmed\":false}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/chatgpt-actions/coach/active-plan?target=WORKOUT").header("Authorization", "Bearer plan-test-token").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(plans, notifications);
    }
    @Test void returnsEmptyCurrentPlanForAuthenticatedOwner() throws Exception {
        var owner = new com.jllado.weightcontrol.domain.User(); when(currentUser.requireUser()).thenReturn(owner);
        when(plans.current(owner)).thenReturn(Optional.empty());
        mvc.perform(get("/api/workout-plans/current").with(user("owner"))).andExpect(status().isNoContent());
        verify(plans).current(owner);
    }
}
