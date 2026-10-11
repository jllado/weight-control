package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.ExerciseRequest;
import com.jllado.weightcontrol.config.SecurityConfig;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.JwtSessionService;
import com.jllado.weightcontrol.security.SessionCookieService;
import com.jllado.weightcontrol.service.CoachAuthAlertService;
import com.jllado.weightcontrol.service.ExerciseImageService;
import com.jllado.weightcontrol.service.ExerciseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExerciseController.class)
@Import(SecurityConfig.class)
class ExerciseEstimateControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExerciseService exercises;
    @MockitoBean ExerciseImageService images;
    @MockitoBean JwtSessionService sessions;
    @MockitoBean SessionCookieService cookies;
    @MockitoBean UserRepository users;
    @MockitoBean CoachAuthAlertService alerts;

    private String request(String extra) {
        return "{\"name\":\"Warm-up\",\"description\":\"Move\",\"trackingMode\":\"SECONDS\",\"exerciseType\":\"WARM_UP\"" + extra + "}";
    }

    @Test void requiresAuthenticationBeforeCatalogMutation() throws Exception {
        mvc.perform(post("/api/workout-exercises").contentType(MediaType.APPLICATION_JSON).content(request(",\"estimatedSecondsPerSet\":60")))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/workout-exercises/1").contentType(MediaType.APPLICATION_JSON).content(request(",\"estimatedSecondsPerSet\":60")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(exercises);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "2147483648", "\"60\""})
    void rejectsInvalidWholeSecondsWithoutCallingTheService(String value) throws Exception {
        mvc.perform(put("/api/workout-exercises/1").with(user("owner")).contentType(MediaType.APPLICATION_JSON)
            .content(request(",\"estimatedSecondsPerSet\":" + value))).andExpect(status().isBadRequest());
        verifyNoInteractions(exercises);
    }

    @Test void acceptsPositiveSecondsAndOptionalOrClearedEstimates() throws Exception {
        when(exercises.create(any())).thenAnswer(call -> response(call.getArgument(0)));
        when(exercises.update(eq(1L), any())).thenAnswer(call -> response(call.getArgument(1)));
        mvc.perform(post("/api/workout-exercises").with(user("owner")).contentType(MediaType.APPLICATION_JSON)
            .content(request(",\"estimatedSecondsPerSet\":60"))).andExpect(status().isOk()).andExpect(jsonPath("$.estimatedSecondsPerSet").value(60));
        mvc.perform(post("/api/workout-exercises").with(user("owner")).contentType(MediaType.APPLICATION_JSON)
            .content(request(""))).andExpect(status().isOk()).andExpect(jsonPath("$.estimatedSecondsPerSet").isEmpty());
        mvc.perform(put("/api/workout-exercises/1").with(user("owner")).contentType(MediaType.APPLICATION_JSON)
            .content(request(",\"estimatedSecondsPerSet\":null"))).andExpect(status().isOk()).andExpect(jsonPath("$.estimatedSecondsPerSet").isEmpty());
    }

    private Exercise response(ExerciseRequest request) {
        var result = new Exercise(); result.setId(1L); result.setName(request.name()); result.setDescription(request.description());
        result.setTrackingMode(request.trackingMode()); result.setExerciseType(request.exerciseType()); result.setEstimatedSecondsPerSet(request.estimatedSecondsPerSet());
        return result;
    }
}
