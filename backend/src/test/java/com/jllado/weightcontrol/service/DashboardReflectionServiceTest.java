package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.SaveReflectionRequest;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.RoutineType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.CalorieRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.DashboardReflectionRepository;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.repository.HabitRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.SicknessRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardReflectionServiceTest {

    @Mock
    private DashboardReflectionRepository reflectionRepository;
    @Mock
    private DailyStatusRepository dailyStatusRepository;
    @Mock
    private WeightRepository weightRepository;
    @Mock
    private BloodPressureRepository bloodPressureRepository;
    @Mock
    private MoodRepository moodRepository;
    @Mock
    private SleepRepository sleepRepository;
    @Mock
    private CalorieRepository calorieRepository;
    @Mock
    private WorkoutRepository workoutRepository;
    @Mock
    private SicknessRepository sicknessRepository;
    @Mock
    private DecisionOutcomeRepository decisionOutcomeRepository;
    @Mock
    private HabitRepository habitRepository;
    @Mock
    private RoutineRepository routineRepository;
    @Mock
    private RoutineCheckinRepository routineCheckinRepository;
    @Mock
    private DailyStatusSnapshotService snapshotService;
    @Mock
    private DecisionOutcomeService decisionOutcomeService;
    private DashboardReflectionService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("test-token", "private@example.com")
        );
        service = new DashboardReflectionService(
            reflectionRepository,
            dailyStatusRepository,
            weightRepository,
            bloodPressureRepository,
            moodRepository,
            sleepRepository,
            calorieRepository,
            workoutRepository,
            sicknessRepository,
            decisionOutcomeRepository,
            habitRepository,
            routineRepository,
            routineCheckinRepository,
            snapshotService,
            decisionOutcomeService,
            properties,
            new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        );
    }

    @Test
    void overviewReturnsCompletedRangeConfigurationAndNewestFirstHistory() {
        User user = user();
        DailyStatus first = status(user.getLastCompletedDashboardDate().minusDays(120));
        DashboardReflection latest = reflection(user.getLastCompletedDashboardDate(), "Latest");
        DashboardReflection older = reflection(user.getLastCompletedDashboardDate().minusDays(1), "Older");
        when(dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)).thenReturn(Optional.of(first));
        when(reflectionRepository.findByUserOrderByReflectionDateDesc(user)).thenReturn(List.of(latest, older));

        ReflectionOverviewResponse overview = service.getOverview(user);

        assertEquals(first.getStatusDate(), overview.firstTrackedDate());
        assertEquals(user.getLastCompletedDashboardDate(), overview.lastCompletedDate());
        assertTrue(overview.actionConfigured());
        assertEquals(List.of("Latest", "Older"), overview.reflections().stream().map(item -> item.title()).toList());
    }

    @Test
    void createRejectsDateAfterLastCompletedDate() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate().plusDays(1);
        when(dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)).thenReturn(Optional.of(status(selectedDate.minusDays(100))));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.getContext(user, selectedDate));

        assertEquals("Reflections can only be generated for completed tracked dates", error.getMessage());
    }

    @Test
    void saveReplacesExistingReflection() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        DashboardReflection existing = reflection(selectedDate, "Existing");
        when(dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)).thenReturn(Optional.of(status(selectedDate.minusDays(100))));
        when(reflectionRepository.findByUserAndReflectionDate(user, selectedDate)).thenReturn(Optional.of(existing));
        when(reflectionRepository.save(existing)).thenReturn(existing);

        DashboardReflection saved = service.save(user, selectedDate, reflectionRequest("Updated"));

        assertEquals("Updated", saved.getTitle());
        assertEquals("ChatGPT", saved.getModel());
        assertEquals(selectedDate.minusDays(89), saved.getWindowStart());
    }

    @Test
    void contextUsesThirtyDetailedDaysAndSixtySummarizedDaysWithoutPrivateFields() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        LocalDate contextStart = selectedDate.minusDays(89);
        LocalDate detailedStart = selectedDate.minusDays(29);
        Mood detailedMood = mood(detailedStart, 4, "Detailed note");
        Mood baselineMood = mood(detailedStart.minusDays(1), 2, "Baseline note must not be sent");
        Weight weight = weight(selectedDate);
        stubInput(user, selectedDate, List.of(detailedMood, baselineMood), List.of(weight));
        when(reflectionRepository.findByUserAndReflectionDate(user, selectedDate)).thenReturn(Optional.empty());
        when(reflectionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        JsonNode context = service.getContext(user, selectedDate);
        String json = context.toString();
        DashboardReflection reflection = service.save(user, selectedDate, reflectionRequest("Balanced progress"));

        assertTrue(json.contains("\"contextStart\":\"" + contextStart + "\""));
        assertTrue(json.contains("\"detailedStart\":\"" + detailedStart + "\""));
        assertTrue(json.contains("Detailed note"));
        assertFalse(json.contains("Baseline note must not be sent"));
        assertTrue(json.contains("\"baselineWeeks\""));
        assertTrue(json.contains("\"moodAverage\":2"));
        assertTrue(json.contains("\"calories\""));
        assertTrue(json.contains("\"workouts\""));
        assertTrue(json.contains("\"sicknesses\""));
        assertTrue(json.contains("\"decisions\""));
        assertFalse(json.contains("photoFrontPath"));
        assertFalse(json.contains(user.getEmail()));
        assertEquals(contextStart, reflection.getWindowStart());
        assertEquals(selectedDate, reflection.getWindowEnd());
        assertEquals("ChatGPT", reflection.getModel());
        assertEquals("Balanced progress", reflection.getTitle());
    }

    @Test
    void contextSummarizesHighVolumeRoutineAndWorkoutDetails() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        LocalDate detailedStart = selectedDate.minusDays(29);
        Routine routine = routine(detailedStart);
        List<RoutineCheckin> checkins = IntStream.range(0, 30)
            .mapToObj(index -> routineCheckin(routine, detailedStart.plusDays(index)))
            .toList();
        Workout workout = workout(selectedDate, 120);
        stubInput(user, selectedDate, List.of(), List.of(), List.of(workout), Map.of(routine, checkins));

        JsonNode context = service.getContext(user, selectedDate);
        String json = context.toString();

        assertTrue(json.contains("\"checkinCount\":30"));
        assertTrue(json.contains("\"lastCheckinDate\":\"" + selectedDate + "\""));
        assertFalse(json.contains("checkinDates"));
        assertTrue(json.contains("\"segmentCount\":120"));
        assertTrue(json.contains("\"totalRepetitions\":1200"));
        assertEquals(
            0,
            new BigDecimal("24000.00").compareTo(
                context.path("workouts").path("days").get(0).path("strengthVolumeKg").decimalValue()
            )
        );
        assertFalse(json.contains("\"segments\""));
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length < 8_000);
    }

    private SaveReflectionRequest reflectionRequest(String title) {
        return new SaveReflectionRequest(
            title,
            "Recent data improved against the baseline.",
            List.of("Mood improved"),
            List.of("Sleep data is sparse"),
            List.of("Keep logging consistently")
        );
    }

    private void stubInput(User user, LocalDate selectedDate, List<Mood> moods, List<Weight> weights) {
        stubInput(user, selectedDate, moods, weights, List.of(), Map.of());
    }

    private void stubInput(
        User user,
        LocalDate selectedDate,
        List<Mood> moods,
        List<Weight> weights,
        List<Workout> workouts,
        Map<Routine, List<RoutineCheckin>> checkins
    ) {
        LocalDate contextStart = selectedDate.minusDays(89);
        DailyStatus first = status(contextStart.minusDays(1));
        when(dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)).thenReturn(Optional.of(first));
        when(dailyStatusRepository.findByUserAndStatusDateBetweenOrderByStatusDateAsc(user, contextStart, selectedDate)).thenReturn(List.of());
        when(weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(contextStart),
            DateTimes.startOfDay(selectedDate).plusDays(1)
        )).thenReturn(weights);
        when(bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(contextStart),
            DateTimes.startOfDay(selectedDate).plusDays(1)
        )).thenReturn(List.of());
        when(moodRepository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, contextStart, selectedDate)).thenReturn(moods);
        when(sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, contextStart, selectedDate)).thenReturn(List.of());
        when(calorieRepository.findByUserAndCalorieDateBetweenOrderByCalorieDateAsc(user, contextStart, selectedDate)).thenReturn(List.of());
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, contextStart, selectedDate)).thenReturn(workouts);
        when(sicknessRepository.findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(user, contextStart, selectedDate)).thenReturn(List.of());
        when(decisionOutcomeRepository.findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(user, contextStart, selectedDate)).thenReturn(List.of());
        when(habitRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of());
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(checkins.keySet().stream().toList());
        checkins.forEach((routine, routineCheckins) ->
            when(routineCheckinRepository.findByRoutineAndCheckedAtBetweenOrderByCheckedAtAsc(
                routine,
                DateTimes.startOfDay(contextStart),
                DateTimes.startOfDay(selectedDate).plusDays(1)
            )).thenReturn(routineCheckins)
        );
        when(decisionOutcomeService.summarize(user, selectedDate)).thenReturn(emptyDecisionSummary());
    }

    private DecisionOutcomeService.Summary emptyDecisionSummary() {
        DecisionOutcomeService.Metrics metrics = new DecisionOutcomeService.Metrics(0, 0, null);
        return new DecisionOutcomeService.Summary(metrics, metrics, metrics, metrics, null, 0);
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("private@example.com");
        user.setBirthDate(LocalDate.of(1985, 1, 1));
        user.setHeightCm(180);
        user.setWeeklyAverageCalorieMaximum(2500);
        user.setTypicalCaloriesSaturday(2600);
        user.setTypicalCaloriesSunday(2600);
        user.setTypicalCaloriesMonday(2200);
        user.setTypicalCaloriesTuesday(2200);
        user.setTypicalCaloriesWednesday(2200);
        user.setTypicalCaloriesThursday(2200);
        user.setTypicalCaloriesFriday(2200);
        user.setLastCompletedDashboardDate(LocalDate.of(2026, 7, 20));
        return user;
    }

    private DailyStatus status(LocalDate date) {
        DailyStatus status = new DailyStatus();
        status.setStatusDate(date);
        return status;
    }

    private DashboardReflection reflection(LocalDate date, String title) {
        DashboardReflection reflection = new DashboardReflection();
        reflection.setReflectionDate(date);
        reflection.setGeneratedAt(java.time.Instant.parse("2026-07-20T12:00:00Z"));
        reflection.setTitle(title);
        return reflection;
    }

    private Mood mood(LocalDate date, int value, String note) {
        Mood mood = new Mood();
        mood.setMoodDate(date);
        mood.setValue(value);
        mood.setNote(note);
        return mood;
    }

    private Weight weight(LocalDate date) {
        Weight weight = new Weight();
        weight.setMeasuredAt(DateTimes.startOfDay(date).plusHours(8));
        weight.setWeight(new BigDecimal("80.00"));
        weight.setFatPercentage(new BigDecimal("20.00"));
        weight.setMusclePercentage(new BigDecimal("40.00"));
        weight.setLostWeight(BigDecimal.ZERO);
        weight.setLostFat(BigDecimal.ZERO);
        weight.setLostMuscle(BigDecimal.ZERO);
        weight.setPhotoFrontPath("/private/front.jpg");
        return weight;
    }

    private Routine routine(LocalDate startDate) {
        Routine routine = new Routine();
        routine.setName("Meditation");
        routine.setStartDate(DateTimes.startOfDay(startDate));
        routine.getTypes().add(RoutineType.MIND);
        return routine;
    }

    private RoutineCheckin routineCheckin(Routine routine, LocalDate date) {
        RoutineCheckin checkin = new RoutineCheckin();
        checkin.setRoutine(routine);
        checkin.setCheckedAt(DateTimes.startOfDay(date).plusHours(8));
        return checkin;
    }

    private Workout workout(LocalDate date, int segmentCount) {
        Exercise exercise = new Exercise();
        exercise.setName("Squat");
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        WorkoutLine line = new WorkoutLine();
        line.setExercise(exercise);
        line.setSegments(IntStream.range(0, segmentCount).mapToObj(index -> {
            WorkoutSegment segment = new WorkoutSegment();
            segment.setRepetitions(10);
            segment.setWeight(new BigDecimal("20.00"));
            return segment;
        }).toList());
        Workout workout = new Workout();
        workout.setWorkoutDate(date);
        workout.setLines(List.of(line));
        return workout;
    }
}
