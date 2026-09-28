package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.UrgePause;
import com.jllado.weightcontrol.api.dto.UrgePauseDtos.*;
import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.UrgePauseService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UrgePauseControllerTest {
    private final UrgePauseService service = mock(UrgePauseService.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private MockMvc mvc;
    @BeforeEach void setup() {
        when(users.requireUser()).thenReturn(new User());
        mvc = MockMvcBuilders.standaloneSetup(new UrgePauseController(users, service)).build();
    }
    @Test void validatesDescriptionsAnswersAndOutcomeEnums() throws Exception {
        mvc.perform(post("/api/urge-pauses").contentType("application/json").content("{\"description\":\"" + "x".repeat(501) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/urge-pauses/1/check-in").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/urge-pauses/1/finish").contentType("application/json").content("{\"outcome\":\"UNKNOWN\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/urge-pauses/1/finish").contentType("application/json").content("{\"reason\":\"" + "x".repeat(501) + "\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void permitsMissingDescriptionAndReturnsServerTime() throws Exception {
        when(service.start(any(), any())).thenReturn(new CurrentResponse(null, OffsetDateTime.now(), null));
        mvc.perform(post("/api/urge-pauses").contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.serverNow").exists()).andExpect(jsonPath("$.pause").isEmpty());
        verify(service).start(any(), eq(new StartRequest(null)));
    }

    @Test void exposesPauseAndResumeActions() throws Exception {
        when(service.pause(any(), eq(7L))).thenReturn(new CurrentResponse(null, OffsetDateTime.now(), null));
        when(service.resume(any(), eq(7L))).thenReturn(new CurrentResponse(null, OffsetDateTime.now(), null));
        mvc.perform(post("/api/urge-pauses/7/pause")).andExpect(status().isOk());
        mvc.perform(post("/api/urge-pauses/7/resume")).andExpect(status().isOk());
        verify(service).pause(any(), eq(7L));
        verify(service).resume(any(), eq(7L));
    }

    @Test void checkInSerializesLinkedWinAndNoCurrentPause() throws Exception {
        var serverNow = OffsetDateTime.now();
        var decision = new DecisionOutcomeResponse(12L, "28/09/2026", LocalDate.of(2026, 9, 28), DecisionOutcomeType.WIN, "Cookies");
        var response = new CurrentResponse(null, serverNow, new RecordMutationResponse<>(decision, List.of()));
        when(service.checkIn(any(), eq(7L), eq(new CheckInRequest(UrgePause.Answer.NOT_ANYMORE)))).thenReturn(response);

        mvc.perform(post("/api/urge-pauses/7/check-in").contentType("application/json").content("{\"answer\":\"NOT_ANYMORE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decisionOutcome.result.outcome").value("WIN"))
            .andExpect(jsonPath("$.decisionOutcome.recordAchievements").isEmpty())
            .andExpect(jsonPath("$.pause").isEmpty());
        verify(service).checkIn(any(), eq(7L), eq(new CheckInRequest(UrgePause.Answer.NOT_ANYMORE)));
    }
}
