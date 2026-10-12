package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineAutomaticTrigger;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.service.RoutineService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RoutineControllerTest {
    @Mock RoutineService service;
    @Mock CurrentUserService currentUserService;
    @Mock DashboardService dashboardService;
    @Mock PersonalRecordMutationService mutationService;
    private MockMvc mvc;
    private User owner;
    private Routine routine;

    @BeforeEach void setUp() {
        var mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new RoutineController(service, currentUserService, dashboardService, mutationService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        owner = new User(); owner.setId(1L);
        routine = new Routine(); routine.setId(2L); routine.setUser(owner);
        routine.setName("Fruit"); routine.setStartDate(OffsetDateTime.parse("2026-08-01T00:00:00+02:00"));
        routine.setCurrentStrike(1); routine.setBestStrike(3);
        routine.setAutomaticTrigger(RoutineAutomaticTrigger.FRUIT_MEAL);
        when(currentUserService.requireUser()).thenReturn(owner);
        when(service.getCheckins(routine)).thenReturn(List.of());
    }

    @Test void listReturnsAssignedRuleAndIndependentEnabledSetting() throws Exception {
        routine.setAutomaticCompletionEnabled(false);
        when(service.findAll(owner)).thenReturn(List.of(routine));
        mvc.perform(get("/api/routines")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].automaticTrigger").value("FRUIT_MEAL"))
            .andExpect(jsonPath("$[0].automaticCompletionEnabled").value(false));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void togglingCompletionRefreshesDashboardAndRecordsAndReturnsStoredFlag(boolean enabled) throws Exception {
        routine.setAutomaticCompletionEnabled(!enabled);
        when(service.requireOwned(owner, 2L)).thenReturn(routine);
        when(mutationService.updateRoutine(eq(owner), eq(2L), any(RoutineRequest.class))).thenAnswer(invocation -> {
            RoutineRequest request = invocation.getArgument(2);
            org.junit.jupiter.api.Assertions.assertEquals(RoutineAutomaticTrigger.FRUIT_MEAL, request.automaticTrigger());
            routine.setAutomaticCompletionEnabled(request.automaticCompletionEnabled());
            return routine;
        });
        mvc.perform(put("/api/routines/2").contentType("application/json").content("""
            {"name":"Fruit","types":["WEIGHT"],"reminderTimes":[],"personalRecordsEnabled":true,
             "automaticTrigger":"FRUIT_MEAL","automaticCompletionEnabled":%s}
            """.formatted(enabled)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.automaticTrigger").value("FRUIT_MEAL"))
            .andExpect(jsonPath("$.automaticCompletionEnabled").value(enabled));
        verify(mutationService).refreshRoutineAutomation(owner);
    }
}
