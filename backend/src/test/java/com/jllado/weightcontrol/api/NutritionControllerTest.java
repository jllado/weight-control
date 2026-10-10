package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetOverrides;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSettingsResponse;
import com.jllado.weightcontrol.config.SecurityConfig;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.security.JwtSessionService;
import com.jllado.weightcontrol.security.SessionCookieService;
import com.jllado.weightcontrol.service.CoachAuthAlertService;
import com.jllado.weightcontrol.service.NutrientTargetService;
import com.jllado.weightcontrol.service.NutritionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NutritionController.class)
@Import(SecurityConfig.class)
class NutritionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NutritionService nutrition;
    @MockitoBean NutrientTargetService targets;
    @MockitoBean CurrentUserService currentUser;
    @MockitoBean JwtSessionService sessions;
    @MockitoBean SessionCookieService cookies;
    @MockitoBean UserRepository users;
    @MockitoBean CoachAuthAlertService alerts;

    @Test
    void requiresAuthenticationAndRejectsInvalidTargetsBeforeMutation() throws Exception {
        mvc.perform(get("/api/nutrition/targets")).andExpect(status().isForbidden());
        mvc.perform(put("/api/nutrition/targets").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        for (String value : List.of("0", "-1", "0.001", "100000000")) {
            mvc.perform(put("/api/nutrition/targets").with(user("owner"))
                .contentType("application/json").content("{\"vitaminDMicrograms\":" + value + "}"))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(targets);
    }

    @Test
    void readsAndUpdatesOnlyTheAuthenticatedAccountIncludingReset() throws Exception {
        User owner = new User();
        User other = new User();
        LocalDate date = LocalDate.of(2026, 8, 12);
        when(currentUser.requireUser()).thenReturn(owner);
        when(targets.settings(owner, date)).thenReturn(new NutrientTargetSettingsResponse(null, new NutrientTargetOverrides(new BigDecimal("20"), null, null)));
        mvc.perform(get("/api/nutrition/targets?asOf=2026-08-12").with(user("owner")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overrides.vitaminDMicrograms").value(20))
            .andExpect(jsonPath("$.user").doesNotExist());
        when(currentUser.requireUser()).thenReturn(other);
        when(targets.settings(other, date)).thenReturn(new NutrientTargetSettingsResponse(null, new NutrientTargetOverrides(null, null, null)));
        mvc.perform(get("/api/nutrition/targets?asOf=2026-08-12").with(user("other")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overrides.vitaminDMicrograms").isEmpty());
        mvc.perform(put("/api/nutrition/targets").with(user("other")).contentType("application/json")
            .content("{\"vitaminDMicrograms\":null,\"omega3Milligrams\":null,\"magnesiumMilligrams\":null}"))
            .andExpect(status().isOk());
        verify(targets).update(eq(other), eq(new NutrientTargetOverrides(null, null, null)), any(LocalDate.class));
        verify(targets, never()).update(eq(owner), any(), any());
    }

    @Test
    void boundedQueriesRequireBothDatesAndPreserveUnfilteredHistory() throws Exception {
        User owner = new User();
        when(currentUser.requireUser()).thenReturn(owner);
        when(nutrition.findAll(owner)).thenReturn(List.of());
        mvc.perform(get("/api/nutrition/daily-summaries").with(user("owner"))).andExpect(status().isOk());
        verify(nutrition).findAll(owner);
        mvc.perform(get("/api/nutrition/daily-summaries?from=2026-08-01").with(user("owner")))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/nutrition/daily-summaries?from=2026-08-01&to=2026-08-12").with(user("owner")))
            .andExpect(status().isOk());
        verify(nutrition).findBetween(owner, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 12));
    }
}
