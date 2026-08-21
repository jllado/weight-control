package com.jllado.weightcontrol.api;

import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.service.WeightService;
import com.jllado.weightcontrol.service.WorkoutService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
class PersonalRecordMutationControllerTest {

    @Mock
    private WorkoutService workoutService;
    @Mock
    private WeightService weightService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private PersonalRecordMutationService mutationService;
    private User user;
    private MockMvc workoutMvc;
    private MockMvc weightMvc;

    @BeforeEach
    void setUp() {
        var converter = new MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules());
        workoutMvc = MockMvcBuilders.standaloneSetup(new WorkoutController(workoutService, currentUserService, mutationService)).setMessageConverters(converter).build();
        weightMvc = MockMvcBuilders.standaloneSetup(new WeightController(weightService, currentUserService, mutationService)).setMessageConverters(converter).build();
        user = new User();
        user.setId(1L);
        when(currentUserService.requireUser()).thenReturn(user);
    }

    @Test
    void workoutCreateReturnsResultAndAchievements() throws Exception {
        Workout workout = workout();
        when(mutationService.createWorkout(eq(user), any())).thenReturn(new PersonalRecordMutationService.MutationResult<>(workout, List.of(achievement(PersonalRecordMetric.WORKOUT_REPETITIONS))));

        workoutMvc.perform(post("/api/workouts").contentType("application/json").content("""
                {"workoutDate":"2026-08-20","note":null,"lines":[{"exerciseId":1,"calories":null,"averageHeartRate":null,"segments":[{"repetitions":12,"durationSeconds":null,"weight":40}]}]}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.id").value(7))
            .andExpect(jsonPath("$.recordAchievements[0].metric").value("WORKOUT_REPETITIONS"));
    }

    @Test
    void weightCreateReturnsResultAndAchievements() throws Exception {
        Weight weight = weight();
        when(mutationService.createWeight(eq(user), any())).thenReturn(new PersonalRecordMutationService.MutationResult<>(weight, List.of(achievement(PersonalRecordMetric.BODY_WEIGHT))));

        weightMvc.perform(post("/api/weights").contentType("application/json").content("""
                {"date":"2026-08-20T08:00:00+02:00","weight":79,"fatPercentage":19,"muscle":65}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.id").value(8))
            .andExpect(jsonPath("$.recordAchievements[0].metric").value("BODY_WEIGHT"));
    }

    private Workout workout() {
        Exercise exercise = new Exercise();
        exercise.setId(1L);
        exercise.setName("Squat");
        exercise.setDescription("Lower-body squat.");
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        Workout workout = new Workout();
        workout.setId(7L);
        workout.setWorkoutDate(LocalDate.of(2026, 8, 20));
        WorkoutLine line = new WorkoutLine();
        line.setExercise(exercise);
        line.setPosition(0);
        WorkoutSegment segment = new WorkoutSegment();
        segment.setPosition(0);
        segment.setRepetitions(12);
        segment.setWeight(new BigDecimal("40"));
        line.getSegments().add(segment);
        workout.getLines().add(line);
        return workout;
    }

    private Weight weight() {
        Weight weight = new Weight();
        weight.setId(8L);
        weight.setMeasuredAt(OffsetDateTime.parse("2026-08-20T08:00:00+02:00"));
        weight.setWeight(new BigDecimal("79"));
        weight.setFatPercentage(new BigDecimal("19"));
        weight.setFat(new BigDecimal("15.01"));
        weight.setMuscle(new BigDecimal("65"));
        weight.setMusclePercentage(new BigDecimal("82.28"));
        weight.setLostWeight(BigDecimal.ZERO);
        weight.setLostFat(BigDecimal.ZERO);
        weight.setLostMuscle(BigDecimal.ZERO);
        return weight;
    }

    private RecordAchievementResponse achievement(PersonalRecordMetric metric) {
        return new RecordAchievementResponse(
            metric, metric.getLabel(), metric.getDomain(), metric.getDirection(), PersonalRecordEventKind.IMPROVED,
            new BigDecimal("12"), new BigDecimal("10"), metric.getUnit(), LocalDate.of(2026, 8, 20),
            new PersonalRecordSubjectResponse(metric.getDomain() == PersonalRecordDomain.BODY ? "BODY" : "EXERCISE", metric.getDomain() == PersonalRecordDomain.BODY ? null : 1L, metric.getDomain() == PersonalRecordDomain.BODY ? "Body" : "Squat"),
            null, new PersonalRecordSourceResponse(metric.getDomain() == PersonalRecordDomain.BODY ? PersonalRecordSourceType.WEIGHT : PersonalRecordSourceType.WORKOUT, 7L, null, null)
        );
    }
}
