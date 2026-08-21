package com.jllado.weightcontrol.api;

import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.PersonalRecordService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class PersonalRecordControllerTest {

    @Mock
    private PersonalRecordService service;
    @Mock
    private CurrentUserService currentUserService;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PersonalRecordController(service, currentUserService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
            .build();
        user = new User();
        user.setId(1L);
        when(currentUserService.requireUser()).thenReturn(user);
    }

    @Test
    void currentUsesOwnedFilters() throws Exception {
        var record = new CurrentRecordResponse(
            PersonalRecordMetric.BODY_WEIGHT, "Lowest weight", PersonalRecordDomain.BODY, PersonalRecordDirection.MINIMUM,
            new BigDecimal("79"), PersonalRecordUnit.KG, LocalDate.of(2026, 8, 8),
            new PersonalRecordSubjectResponse("BODY", null, "Body"), null,
            new PersonalRecordSourceResponse(PersonalRecordSourceType.WEIGHT, 2L, null, null)
        );
        when(service.current(user, PersonalRecordDomain.BODY, PersonalRecordMetric.BODY_WEIGHT, null)).thenReturn(List.of(record));

        mockMvc.perform(get("/api/personal-records/current").param("domain", "BODY").param("metric", "BODY_WEIGHT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].metric").value("BODY_WEIGHT"))
            .andExpect(jsonPath("$[0].value").value(79));
    }

    @Test
    void historyPassesPaginationAndWorkoutFilters() throws Exception {
        when(service.history(user, PersonalRecordDomain.WORKOUT, null, null, Set.of(7L), 1, 10))
            .thenReturn(new HistoryPageResponse(List.of(), 1, 10, 12, 2));

        mockMvc.perform(get("/api/personal-records/history")
                .param("domain", "WORKOUT").param("workoutId", "7").param("page", "1").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.totalElements").value(12));
    }
}
