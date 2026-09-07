package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DecisionOutcomeService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DecisionOutcomeControllerTest {
    private final DecisionOutcomeRepository repository = mock(DecisionOutcomeRepository.class);
    private final DecisionOutcomeService service = new DecisionOutcomeService(repository);
    private final PersonalRecordMutationService mutation = mock(PersonalRecordMutationService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final User user = new User();
    private MockMvc mvc;

    @BeforeEach void setup() {
        user.setId(1L);
        when(currentUser.requireUser()).thenReturn(user);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mutation.createDecisionOutcome(eq(user), any())).thenAnswer(invocation ->
            new PersonalRecordMutationService.MutationResult<>(service.create(user, invocation.getArgument(1)), List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new DecisionOutcomeController(currentUser, mutation, service)).build();
    }

    @Test void acceptsLegacyAndOptionalReasonsWithoutChangingMutationEnvelope() throws Exception {
        for (String reason : List.of("", ",\"reason\":null", ",\"reason\":\"  \"")) {
            mvc.perform(post("/api/decision-outcomes").contentType("application/json")
                .content("{\"date\":\"2026-08-11\",\"outcome\":\"WIN\"" + reason + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.reason").isEmpty())
                .andExpect(jsonPath("$.recordAchievements").isArray());
        }
        mvc.perform(post("/api/decision-outcomes").contentType("application/json")
            .content("{\"date\":\"2026-08-11\",\"outcome\":\"MISS\",\"reason\":\"  Skipped my walk  \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.result.reason").value("Skipped my walk"));
    }

    @Test void validatesReasonLengthOnBothWrites() throws Exception {
        String reason = "x".repeat(501);
        mvc.perform(post("/api/decision-outcomes").contentType("application/json")
            .content("{\"date\":\"2026-08-11\",\"outcome\":\"WIN\",\"reason\":\"" + reason + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/decision-outcomes/1/reason").contentType("application/json").content("{\"reason\":\"" + reason + "\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(repository, mutation);
    }

    @Test void listsOwnedHistoryAndEditsOnlyReasonWithoutRecordMutation() throws Exception {
        var entry = service.create(user, new DecisionOutcomeRequest(LocalDate.of(2026, 8, 11), DecisionOutcomeType.WIN, null));
        entry.setId(7L);
        when(repository.findById(7L)).thenReturn(Optional.of(entry));
        when(repository.findByUserOrderByOutcomeDateDescIdDesc(user)).thenReturn(List.of(entry));
        mvc.perform(get("/api/decision-outcomes")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(7))
            .andExpect(jsonPath("$[0].reason").isEmpty()).andExpect(jsonPath("$[0].user").doesNotExist());
        for (String reason : List.of("Walked", "x".repeat(500), "")) {
            mvc.perform(put("/api/decision-outcomes/7/reason").contentType("application/json").content("{\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("WIN"))
                .andExpect(jsonPath("$.reason").value(reason.isEmpty() ? null : reason));
        }
        verifyNoInteractions(mutation);
    }

    @Test void rejectsMissingAndOtherUsersEntries() throws Exception {
        var other = new User(); other.setId(2L);
        var entry = new DecisionOutcome(); entry.setUser(other);
        when(repository.findById(2L)).thenReturn(Optional.of(entry));
        for (long id : List.of(1L, 2L)) {
            mvc.perform(put("/api/decision-outcomes/" + id + "/reason").contentType("application/json").content("{\"reason\":\"Changed\"}"))
                .andExpect(status().isNotFound());
        }
        verify(repository, never()).save(any());
    }
}
