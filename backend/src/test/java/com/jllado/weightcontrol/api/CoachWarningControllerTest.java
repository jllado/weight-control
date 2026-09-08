package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jllado.weightcontrol.api.dto.CoachDtos.*;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CoachWarningService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CoachWarningControllerTest {
    @Test void warningWritesDoNotRequireConfirmationButValidateTheirContract() throws Exception {
        var service = mock(CoachWarningService.class);
        var current = mock(CurrentUserService.class);
        var user = new User();
        when(current.requireUser()).thenReturn(user);
        var mvc = MockMvcBuilders.standaloneSetup(new ChatGptCoachWarningActionController(service, current), new CoachWarningController(service, current)).build();
        String body = """
            {"requestKey":"569a7494-4e32-41d7-8ac5-b03103342261","type":"RECOVERY_STRAIN",
            "content":{"explanation":"Pattern","evidence":"Sep 1–7 compared with prior days","action":"Rest","reviewedDate":"2026-09-08"}}
            """;
        mvc.perform(post("/api/chatgpt-actions/coach/warnings").contentType("application/json").content("{\"create\":" + body + "}")).andExpect(status().isOk());
        verify(service).write(eq(user), any(WarningWriteRequest.class));
        mvc.perform(post("/api/chatgpt-actions/coach/warnings").contentType("application/json").content(body.replace("RECOVERY_STRAIN", "DIAGNOSIS"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatgpt-actions/coach/warnings").contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatgpt-actions/coach/warnings").contentType("application/json").content("{\"version\":0,\"reviewedDate\":\"2026-09-08\",\"rationale\":\"\"}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatgpt-actions/coach/warnings").contentType("application/json").content("{\"create\":" + body + ",\"id\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/chatgpt-actions/coach/warnings").contentType("application/json").content("{\"update\":{\"version\":0,\"content\":{\"explanation\":\"X\",\"evidence\":\"Y\",\"action\":\"Z\",\"reviewedDate\":\"2026-09-08\"}}}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/coach-warnings").contentType("application/json").content(body)).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/coach-warnings/1").contentType("application/json").content(body)).andExpect(status().isNotFound());
    }
}
