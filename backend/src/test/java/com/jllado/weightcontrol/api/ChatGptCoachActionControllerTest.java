package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachCatalogResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachContextResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachDataSemantics;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.BadRequestException;
import com.jllado.weightcontrol.service.HealthDataContextService;
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
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ChatGptCoachActionController controller = new ChatGptCoachActionController(
            healthDataContextService,
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
}
