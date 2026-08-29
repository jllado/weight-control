package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.PushDtos.ReleaseNotificationRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.AgendaResponse;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.AgendaService;
import com.jllado.weightcontrol.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PushControllerTest {

    private static final String COMMIT_SHA = "d88c96a4c5ac69e262e6d92fbb42c91e220c74a5";

    @Mock
    private PushNotificationService service;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private AppProperties properties;
    @Mock
    private AgendaService agendaService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PushController(service, currentUserService, properties, agendaService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void releaseNotificationForwardsValidatedGitMetadata() throws Exception {
        ReleaseNotificationRequest request = new ReleaseNotificationRequest(COMMIT_SHA, "Allow workout exercise reordering");

        mockMvc.perform(post("/api/push/release-notification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNoContent());

        verify(service).sendAppUpdate(request);
    }

    @Test
    void releaseNotificationRejectsInvalidGitMetadata() throws Exception {
        mockMvc.perform(post("/api/push/release-notification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReleaseNotificationRequest("d88c96a", "Feature"))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/push/release-notification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReleaseNotificationRequest(COMMIT_SHA, "x".repeat(81)))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void agendaReturnsTodayScheduleForTheAuthenticatedUser() throws Exception {
        User user = new User();
        AgendaResponse agenda = new AgendaResponse(java.time.LocalDate.of(2026, 8, 29), "Europe/Madrid", java.util.List.of());
        when(currentUserService.requireUser()).thenReturn(user);
        when(agendaService.today(user)).thenReturn(agenda);

        mockMvc.perform(get("/api/push/agenda"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.date").exists())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.timeZone").value("Europe/Madrid"));

        verify(agendaService).today(user);
    }
}
