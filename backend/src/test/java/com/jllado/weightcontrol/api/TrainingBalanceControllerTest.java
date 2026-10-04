package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.config.SecurityConfig;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.*;
import com.jllado.weightcontrol.service.*;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WorkoutController.class)
@Import(SecurityConfig.class)
class TrainingBalanceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean WorkoutService workouts;
    @MockitoBean TrainingBalanceService balance;
    @MockitoBean PersonalRecordMutationService mutations;
    @MockitoBean PersonalRecordService records;
    @MockitoBean CurrentUserService currentUser;
    @MockitoBean JwtSessionService sessions;
    @MockitoBean SessionCookieService cookies;
    @MockitoBean UserRepository users;
    @MockitoBean CoachAuthAlertService alerts;

    @Test void authenticatesValidatesDateAndReturnsOnlyTheAuthenticatedOwnersWeek() throws Exception {
        mvc.perform(get("/api/workouts/training-balance?date=2026-10-04")).andExpect(status().isForbidden());
        mvc.perform(get("/api/workouts/training-balance").with(user("owner"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/workouts/training-balance?date=invalid").with(user("owner"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/workouts/training-balance?date=2026-02-30").with(user("owner"))).andExpect(status().isBadRequest());
        verifyNoInteractions(balance);
        var owner = new User(); owner.setId(7L); when(currentUser.requireUser()).thenReturn(owner);
        var date = LocalDate.of(2026, 10, 4);
        when(balance.week(owner, date)).thenReturn(new TrainingBalanceResponse(date.minusDays(1), date.plusDays(5), 5,
            Arrays.stream(PrimaryMuscleGroup.values()).map(group -> new TrainingBalanceGroup(group, group == PrimaryMuscleGroup.CHEST ? 3 : group == PrimaryMuscleGroup.BACK ? 2 : 0)).toList()));
        mvc.perform(get("/api/workouts/training-balance?date=2026-10-04").with(user("owner")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.weekStart").value("2026-10-03"))
            .andExpect(jsonPath("$.weekEnd").value("2026-10-09")).andExpect(jsonPath("$.totalSets").value(5))
            .andExpect(jsonPath("$.groups.length()").value(11)).andExpect(jsonPath("$.groups[0].muscleGroup").value("CHEST"))
            .andExpect(jsonPath("$.groups[0].sets").value(3)).andExpect(jsonPath("$.groups[1].sets").value(2)).andExpect(jsonPath("$.groups[10].sets").value(0));
        verify(balance).week(owner, date);
    }
}
