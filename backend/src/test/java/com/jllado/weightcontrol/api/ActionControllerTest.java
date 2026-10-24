package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jllado.weightcontrol.config.SecurityConfig;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.*;
import com.jllado.weightcontrol.service.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ActionController.class) @Import(SecurityConfig.class)
class ActionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActionService actions;
    @MockitoBean ActionRecommendationService recommendations;
    @MockitoBean CurrentUserService currentUser;
    @MockitoBean JwtSessionService sessions;
    @MockitoBean SessionCookieService cookies;
    @MockitoBean UserRepository users;
    @MockitoBean CoachAuthAlertService alerts;

    @Test void requiresAuthenticationAndValidatesDatesAndBodiesBeforeMutations() throws Exception {
        mvc.perform(get("/api/actions?date=2026-10-24")).andExpect(status().isForbidden());
        mvc.perform(post("/api/actions").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/actions?date=invalid").with(user("owner"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/actions").with(user("owner")).contentType("application/json")
            .content("{\"wording\":\"Action\",\"startDate\":\"2026-10-24\",\"targetDays\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/actions").with(user("owner")).contentType("application/json")
            .content("{\"wording\":\" \",\"startDate\":\"2026-10-24\",\"targetDays\":14}")).andExpect(status().isBadRequest());
        verifyNoInteractions(actions);
    }

    @Test void routesUseTheAuthenticatedOwnerAndDatedCheckinOperation() throws Exception {
        var owner = new User(); owner.setId(7L); when(currentUser.requireUser()).thenReturn(owner);
        var date = LocalDate.of(2026,10,24);
        when(actions.list(owner,date,"ACTIVE")).thenReturn(List.of());
        mvc.perform(get("/api/actions?date=2026-10-24").with(user("owner"))).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(put("/api/actions/5/checkins/2026-10-24").with(user("owner"))).andExpect(status().isOk());
        mvc.perform(delete("/api/actions/5/checkins/2026-10-24").with(user("owner"))).andExpect(status().isOk());
        verify(actions).check(owner,5L,date,true); verify(actions).check(owner,5L,date,false);
        when(actions.find(owner,8L,date)).thenThrow(new NotFoundException("Action not found"));
        mvc.perform(get("/api/actions/8?date=2026-10-24").with(user("owner"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/actions/suggestions").with(user("owner"))).andExpect(status().isOk());
        verify(recommendations).suggestions(owner);
    }

    @Test void sourceResolutionAndEveryLifecycleWriteKeepTheirOwnedContracts() throws Exception {
        var owner = new User(); owner.setId(7L); when(currentUser.requireUser()).thenReturn(owner);
        var date = LocalDate.of(2026,10,24);
        var row = new com.jllado.weightcontrol.api.dto.ActionDtos.ActionResponse(5L, "Action", date, 14,
            false, null, null, null, null, "ACTIVE", 0, 0, 0, null, false, List.of());
        var key = "weekly:42:" + "a".repeat(64);
        when(recommendations.resolve(owner,key)).thenReturn(
            new com.jllado.weightcontrol.api.dto.ActionDtos.SuggestionResponse(key, "Action", "Weekly summary", List.of("Routines"), null));
        mvc.perform(get("/api/actions/suggestions/" + key).with(user("owner"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.wording").value("Action")).andExpect(jsonPath("$.sourceKey").value(key));
        when(recommendations.resolve(owner,key)).thenThrow(new BadRequestException("This recommendation has changed"));
        mvc.perform(get("/api/actions/suggestions/" + key).with(user("owner"))).andExpect(status().isBadRequest())
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertEquals("This recommendation has changed", result.getResolvedException().getMessage()));
        when(actions.create(eq(owner),any())).thenReturn(row);
        when(actions.update(eq(owner),eq(5L),any())).thenReturn(row);
        when(actions.stop(owner,5L)).thenReturn(row);
        when(actions.restart(eq(owner),eq(5L),any())).thenReturn(row);
        String body = "{\"wording\":\"Action\",\"startDate\":\"2026-10-24\",\"targetDays\":14}";
        mvc.perform(post("/api/actions").with(user("owner")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
        mvc.perform(put("/api/actions/5").with(user("owner")).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.goalLocked").value(false));
        mvc.perform(post("/api/actions/5/stop").with(user("owner"))).andExpect(status().isOk());
        mvc.perform(post("/api/actions/5/restart").with(user("owner")).contentType("application/json").content(body)).andExpect(status().isOk());
        verify(actions).stop(owner,5L);
        verify(actions).restart(eq(owner),eq(5L),any());
    }
}
