package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.api.dto.UrgePauseDtos.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.UrgePauseService;
import java.time.OffsetDateTime;
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
        when(service.start(any(), any())).thenReturn(new CurrentResponse(null, OffsetDateTime.now()));
        mvc.perform(post("/api/urge-pauses").contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.serverNow").exists()).andExpect(jsonPath("$.pause").isEmpty());
        verify(service).start(any(), eq(new StartRequest(null)));
    }
}
