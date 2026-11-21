package com.jllado.weightcontrol.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyCaloriePlan;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeeklyCaloriePlanRepository;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WeeklyCaloriePlanService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class WeeklyCaloriePlanControllerTest {
    @Mock private WeeklyCaloriePlanRepository repository;
    @Mock private UserRepository users;
    @Mock private CurrentUserService currentUserService;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new WeeklyCaloriePlanController(new WeeklyCaloriePlanService(repository, users, mock(org.springframework.context.ApplicationEventPublisher.class)), currentUserService))
            .setValidator(validator)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(
                new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))).build();
        user = new User();
        user.setId(1L);
    }

    @Test
    void getReturnsNoContentForAnUnplannedWeekAndScopesTheLookupToTheSessionUser() throws Exception {
        LocalDate start = LocalDate.of(2026, 8, 15);
        when(currentUserService.requireUser()).thenReturn(user);
        mockMvc.perform(get("/api/weekly-calorie-plans/{weekStart}", start)).andExpect(status().isNoContent());
        verify(repository).findByUserAndWeekStartBetweenOrderByWeekStartAsc(user, start, start);
    }

    @Test
    void putReturnsTheSavedDatedPlan() throws Exception {
        LocalDate start = LocalDate.of(2026, 8, 15);
        when(currentUserService.requireUser()).thenReturn(user);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        doAnswer(call -> {
            Iterable<WeeklyCaloriePlan> rows = call.getArgument(0);
            var saved = rows.iterator().next();
            saved.setRevision(0L);
            return List.of(saved);
        }).when(repository).saveAllAndFlush(any());
        mockMvc.perform(put("/api/weekly-calorie-plans/{weekStart}", start).contentType("application/json").content(requestJson(start)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.weekStart").value("2026-08-15"))
            .andExpect(jsonPath("$.revision").value(0))
            .andExpect(jsonPath("$.days[0].calories").value(3000))
            .andExpect(jsonPath("$.days[6].date").value("2026-08-21"));
        verify(repository).saveAllAndFlush(any());
    }

    @Test
    void putRejectsUrlAndBodyWeekMismatchBeforeWriting() throws Exception {
        LocalDate start = LocalDate.of(2026, 8, 15);
        mockMvc.perform(put("/api/weekly-calorie-plans/2026-08-15").contentType("application/json").content(requestJson(start.plusDays(7))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void putRejectsNegativeFractionalAndIncompleteDailyValues() throws Exception {
        when(currentUserService.requireUser()).thenReturn(user);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        String valid = requestJson(LocalDate.of(2026, 8, 15));
        mockMvc.perform(put("/api/weekly-calorie-plans/2026-08-15").contentType("application/json")
                .content(valid.replace("\"calories\":3000", "\"calories\":-1")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/weekly-calorie-plans/2026-08-15").contentType("application/json")
                .content(valid.replace("\"calories\":3000", "\"calories\":3000.5")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/weekly-calorie-plans/2026-08-15").contentType("application/json")
                .content(valid.replace(",{\"date\":\"2026-08-21\",\"calories\":2000}", "")))
            .andExpect(status().isBadRequest());
    }

    private String requestJson(LocalDate start) {
        return "{\"weekStart\":\"" + start + "\",\"revision\":null,\"days\":[" +
            "{\"date\":\"" + start + "\",\"calories\":3000}," +
            java.util.stream.IntStream.range(1, 7).mapToObj(index -> "{\"date\":\"" + start.plusDays(index) + "\",\"calories\":2000}").collect(java.util.stream.Collectors.joining(",")) + "]}";
    }
}
