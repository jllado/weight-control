package com.jllado.weightcontrol.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jllado.weightcontrol.config.SecurityConfig;
import com.jllado.weightcontrol.domain.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.security.JwtSessionService;
import com.jllado.weightcontrol.security.SessionCookieService;
import com.jllado.weightcontrol.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = WorkoutController.class, properties = {"app.chat-gpt-actions.token=sauna-test-token", "app.chat-gpt-actions.user-email=owner@example.com"})
@Import(SecurityConfig.class)
class WorkoutSaunaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean WorkoutService workouts;
    @MockitoBean CurrentUserService currentUser;
    @MockitoBean PersonalRecordMutationService mutations;
    @MockitoBean PersonalRecordService records;
    @MockitoBean TrainingBalanceService trainingBalance;
    @MockitoBean JwtSessionService sessions;
    @MockitoBean SessionCookieService cookies;
    @MockitoBean UserRepository users;
    @MockitoBean CoachAuthAlertService alerts;

    @Test void noEligibleSourceReturnsNoContentWithoutMutationEffects() throws Exception {
        User owner = new User(); owner.setId(1L);
        when(currentUser.requireUser()).thenReturn(owner);
        mvc.perform(get("/api/workouts/sauna/latest").param("through", "2026-11-07").with(user("owner")))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        verifyNoInteractions(mutations, records, trainingBalance);
    }

    @Test void returnsTheExistingWorkoutResponseWithExactRoundsUnitsAndSegmentPrecision() throws Exception {
        User owner = new User(); owner.setId(1L); LocalDate through=LocalDate.of(2026,11,7);
        Workout source = new Workout(); source.setId(31L); source.setUser(owner); source.setWorkoutDate(through);
        source.setSaunaSession(true);source.setSaunaRoundsMinutes(List.of(15,8));source.setPlannedSaunaRoundsMinutes(List.of(12));
        source.setDurationMinutes(33);source.setWarmUpMinutes(1);source.setTrainingMinutes(2);source.setStretchingMinutes(3);source.setCardioMinutes(4);
        source.setNote("Source note");source.setPlannedSessionName("Source plan");
        Exercise stretch = new Exercise();stretch.setId(10L);stretch.setName("Recorded stretch");stretch.setDescription("Breaths");
        stretch.setTrackingMode(ExerciseTrackingMode.SECONDS);stretch.setExerciseType(ExerciseType.STRETCHING);
        WorkoutLine breath = new WorkoutLine();breath.setWorkout(source);breath.setExercise(stretch);breath.setPosition(0);breath.setStretchingUnit(StretchingUnit.BREATHS);breath.setExerciseDurationSeconds(42);
        WorkoutSegment counted = new WorkoutSegment();counted.setWorkoutLine(breath);counted.setPosition(0);counted.setBreaths(2);breath.getSegments().add(counted);
        Exercise timed = new Exercise();timed.setId(11L);timed.setName("Recorded hold");timed.setDescription("Exact duration");timed.setTrackingMode(ExerciseTrackingMode.SECONDS);timed.setExerciseType(ExerciseType.TRAINING);
        WorkoutLine hold = new WorkoutLine();hold.setWorkout(source);hold.setExercise(timed);hold.setPosition(1);
        WorkoutSegment seconds = new WorkoutSegment();seconds.setWorkoutLine(hold);seconds.setPosition(0);seconds.setDurationSeconds(93);seconds.setWeight(new BigDecimal("12.35"));hold.getSegments().add(seconds);
        source.getLines().addAll(List.of(breath,hold));
        when(currentUser.requireUser()).thenReturn(owner);when(workouts.findLatestSauna(owner,through)).thenReturn(Optional.of(source));
        mvc.perform(get("/api/workouts/sauna/latest").param("through",through.toString()).with(user("owner")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.saunaRoundsMinutes[0]").value(15)).andExpect(jsonPath("$.saunaRoundsMinutes[1]").value(8))
            .andExpect(jsonPath("$.lines[0].stretchingUnit").value("BREATHS")).andExpect(jsonPath("$.lines[0].sets[0].breaths").value(2))
            .andExpect(jsonPath("$.lines[1].sets[0].durationSeconds").value(93)).andExpect(jsonPath("$.lines[1].sets[0].weight").value(12.35))
            .andExpect(jsonPath("$.durationMinutes").value(33)).andExpect(jsonPath("$.warmUpMinutes").value(1))
            .andExpect(jsonPath("$.trainingMinutes").value(2)).andExpect(jsonPath("$.stretchingMinutes").value(3)).andExpect(jsonPath("$.cardioMinutes").value(4))
            .andExpect(jsonPath("$.note").value("Source note")).andExpect(jsonPath("$.plannedSessionName").value("Source plan"))
            .andExpect(jsonPath("$.plannedSaunaRoundsMinutes[0]").value(12)).andExpect(jsonPath("$.recordingKey").doesNotExist());
        verify(workouts).findLatestSauna(owner,through);verifyNoInteractions(mutations,records,trainingBalance);
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/workouts/sauna/latest").param("through", "2026-11-07"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(workouts, mutations);
    }

    @Test void cutoffIsRequiredAndMustBeAnIsoDate() throws Exception {
        mvc.perform(get("/api/workouts/sauna/latest").with(user("owner"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/workouts/sauna/latest").param("through", "not-a-date").with(user("owner")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(workouts, mutations);
    }
}
