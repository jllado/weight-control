package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.HealthConstraintSource;
import com.jllado.weightcontrol.domain.HealthConstraintType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.HealthConstraintService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class HealthConstraintControllerTest {

    @Mock
    private HealthConstraintService service;
    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthConstraintController(service, currentUserService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules()))
            .build();
        user = new User();
        user.setId(1L);
    }

    @Test
    void listReturnsOwnedConstraints() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(service.findAll(user)).thenReturn(List.of(constraint()));

        mockMvc.perform(get("/api/health-constraints"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Prescribed core exercises"));
    }

    @Test
    void createUpdateAndDeleteUseCurrentUser() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(service.create(org.mockito.ArgumentMatchers.eq(user), any())).thenReturn(constraint());
        when(service.update(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq(10L), any()))
            .thenReturn(constraint());

        mockMvc.perform(post("/api/health-constraints")
                .contentType("application/json")
                .content(requestJson("Prescribed core exercises")))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/health-constraints/10")
                .contentType("application/json")
                .content(requestJson("Prescribed core exercises")))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/health-constraints/10"))
            .andExpect(status().isOk());

        verify(service).delete(user, 10L);
    }

    @Test
    void createRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/health-constraints")
                .contentType("application/json")
                .content(requestJson("")))
            .andExpect(status().isBadRequest());
    }

    private String requestJson(String title) {
        return """
            {
              "type": "CLINICIAN_GUIDANCE",
              "title": "%s",
              "details": "Bird dogs and side planks three times per week",
              "source": "PHYSIOTHERAPIST",
              "startDate": "2026-08-01",
              "endDate": null,
              "active": true
            }
            """.formatted(title);
    }

    private HealthConstraint constraint() {
        HealthConstraint constraint = new HealthConstraint();
        constraint.setId(10L);
        constraint.setUser(user);
        constraint.setType(HealthConstraintType.CLINICIAN_GUIDANCE);
        constraint.setTitle("Prescribed core exercises");
        constraint.setDetails("Bird dogs and side planks three times per week");
        constraint.setSource(HealthConstraintSource.PHYSIOTHERAPIST);
        constraint.setStartDate(LocalDate.of(2026, 8, 1));
        constraint.setActive(true);
        return constraint;
    }
}
