package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jllado.weightcontrol.config.SecurityConfig;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.JwtSessionService;
import com.jllado.weightcontrol.security.SessionCookieService;
import com.jllado.weightcontrol.service.CoachAuthAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StretchingSetController.class)
@Import(SecurityConfig.class)
class StretchingSetControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean com.jllado.weightcontrol.service.StretchingSetService sets;
    @MockitoBean com.jllado.weightcontrol.security.CurrentUserService currentUser;
    @MockitoBean JwtSessionService sessions;
    @MockitoBean SessionCookieService cookies;
    @MockitoBean UserRepository users;
    @MockitoBean CoachAuthAlertService alerts;

    @Test void requiresAuthenticationAndValidatesRequests() throws Exception {
        mvc.perform(get("/api/stretching-sets")).andExpect(status().isForbidden());
        mvc.perform(post("/api/stretching-sets").with(user("owner")).contentType("application/json").content("{\"name\":\"Invalid\",\"entries\":[]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(sets);
    }
    @Test void returnsOnlySetDataForCurrentUser() throws Exception {
        var owner = new com.jllado.weightcontrol.domain.User();
        when(currentUser.requireUser()).thenReturn(owner);
        when(sets.findAll(owner)).thenReturn(java.util.List.of(new com.jllado.weightcontrol.api.dto.WorkoutDtos.StretchingSetResponse(1L, "Morning", java.util.List.of(new com.jllado.weightcontrol.api.dto.WorkoutDtos.StretchingSetEntryRequest(2L, java.util.List.of(30))))));
        mvc.perform(get("/api/stretching-sets").with(user("owner"))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].entries[0].durations[0]").value(30)).andExpect(jsonPath("$[0].user").doesNotExist());
    }
}
