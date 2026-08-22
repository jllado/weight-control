package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.SaveReflectionRequest;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.DashboardReflection;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.RoutineType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutAssessment;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.CoachingPlanRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.DashboardReflectionRepository;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.repository.HabitRepository;
import com.jllado.weightcontrol.repository.HealthConstraintRepository;
import com.jllado.weightcontrol.repository.LipidPanelRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private LipidPanelRepository lipidPanelRepository;
    @Mock
    private MoodRepository moodRepository;
    @Mock
    private SleepRepository sleepRepository;
    @Mock
    private CalorieService calorieService;
    @Mock
    private MealService mealService;
    @Mock
    private NutritionService nutritionService;
    @Mock
    private FastingPeriodService fastingPeriodService;
    @Mock
    private WorkoutRepository workoutRepository;
    @Mock
    private SicknessRepository sicknessRepository;
    @Mock
    private BackPainEpisodeRepository backPainEpisodeRepository;
    @Mock
    private DecisionOutcomeRepository decisionOutcomeRepository;
    @Mock
    private HabitRepository habitRepository;
    @Mock
    private HealthConstraintRepository healthConstraintRepository;
    @Mock
    private CoachingPlanRepository coachingPlanRepository;
    @Mock
    private RoutineRepository routineRepository;
    @Mock
    private RoutineCheckinRepository routineCheckinRepository;
    @Mock
    private DailyStatusSnapshotService snapshotService;
    @Mock
    private DecisionOutcomeService decisionOutcomeService;
    @Mock
    private ProgressPhotoService progressPhotoService;
    private HealthDataContextService healthDataContextService;
    private DashboardReflectionService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("test-token", "private@example.com", "https://test.example", "test-file-signing-secret-32-bytes-long"),
            new AppProperties.Push(false, "", "", "mailto:test@example.com", ""),
            new AppProperties.WeeklySummary(false, "", "", "", "")
        );
        healthDataContextService = new HealthDataContextService(
            reflectionRepository,
            dailyStatusRepository,
            weightRepository,
            bloodPressureRepository,
            lipidPanelRepository,
            moodRepository,
            sleepRepository,
            calorieService,
            mealService,
            nutritionService,
            fastingPeriodService,
            workoutRepository,
            sicknessRepository,
            backPainEpisodeRepository,
            decisionOutcomeRepository,
            habitRepository,
            healthConstraintRepository,
            coachingPlanRepository,
            routineRepository,
            routineCheckinRepository,
            decisionOutcomeService,
            new WeeklyMetricsCalculator(),
            progressPhotoService,
            org.mockito.Mockito.mock(PersonalRecordService.class)
        );
        service = new DashboardReflectionService(
            reflectionRepository,
            dailyStatusRepository,
            snapshotService,
            healthDataContextService,
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

        List<String> fields = new ArrayList<>();
        context.fieldNames().forEachRemaining(fields::add);
        assertEquals(List.of(
            "selectedDate",
            "contextStart",
            "detailedStart",
            "baselineEnd",
            "profile",
            "dataSemantics",
            "recentReflections",
            "dailyStatuses",
            "habits",
            "routines",
            "weights",
            "bloodPressures",
            "moods",
            "sleeps",
            "calories",
            "workouts",
            "sicknesses",
            "decisions",
            "decisionSummary",
            "weekProgress",
            "baselineWeeks"
        ), fields);
        assertTrue(json.contains("\"contextStart\":\"" + contextStart + "\""));
        assertTrue(json.contains("\"detailedStart\":\"" + detailedStart + "\""));
        assertTrue(json.contains("Detailed note"));
        assertTrue(json.contains("\"period\":\"EVENING\""));
        assertFalse(json.contains("Baseline note must not be sent"));
        assertTrue(json.contains("\"baselineWeeks\""));
        assertTrue(json.contains("\"moodAverage\":2"));
        assertTrue(json.contains("\"calories\""));
        assertTrue(json.contains("\"workouts\""));
        assertTrue(json.contains("\"sicknesses\""));
        assertTrue(json.contains("\"decisions\""));
        assertFalse(json.contains("\"id\""));
        assertFalse(json.contains("photoFrontPath"));
        assertFalse(json.contains("/private/front.jpg"));
        assertFalse(json.contains(user.getEmail()));
        assertEquals(contextStart, reflection.getWindowStart());
        assertEquals(selectedDate, reflection.getWindowEnd());
        assertEquals("ChatGPT", reflection.getModel());
        assertEquals("Balanced progress", reflection.getTitle());
    }

    @Test
    void contextKeepsMissingDataUnknown() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        stubInput(user, selectedDate, List.of(), List.of());

        JsonNode context = service.getContext(user, selectedDate);
        JsonNode currentPeriod = context.path("weekProgress").path("currentPeriod");

        assertTrue(context.path("dailyStatuses").isEmpty());
        assertTrue(context.path("weights").isEmpty());
        assertTrue(context.path("moods").isEmpty());
        assertTrue(context.path("workouts").path("days").isEmpty());
        assertFalse(currentPeriod.has("dashboard"));
        assertFalse(currentPeriod.has("weight"));
        assertFalse(currentPeriod.has("bloodPressure"));
        assertFalse(currentPeriod.has("moodAverage"));
        assertFalse(currentPeriod.has("sleep"));
        assertFalse(currentPeriod.path("calories").has("averageCalories"));
        assertFalse(currentPeriod.path("calories").has("averageDifferenceFromTarget"));
        verify(snapshotService).getOrBuild(user, selectedDate);
    }

    @Test
    void contextIncludesSevenNewestReflectionsBeforeSelectedDate() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        List<DashboardReflection> recentReflections = IntStream.rangeClosed(1, 7)
            .mapToObj(offset -> reflection(selectedDate.minusDays(offset), "Reflection " + offset))
            .toList();
        stubInput(user, selectedDate, List.of(), List.of());
        when(reflectionRepository.findTop7ByUserAndReflectionDateBeforeOrderByReflectionDateDesc(user, selectedDate))
            .thenReturn(recentReflections);

        JsonNode context = service.getContext(user, selectedDate);
        JsonNode history = context.path("recentReflections");

        assertEquals(7, history.size());
        assertEquals(selectedDate.minusDays(1).toString(), history.get(0).path("reflectionDate").textValue());
        assertEquals("Reflection 1", history.get(0).path("title").textValue());
        assertEquals("Summary for Reflection 1", history.get(0).path("summary").textValue());
        assertEquals("Positive for Reflection 1", history.get(0).path("positiveSignals").get(0).textValue());
        assertEquals("Watchout for Reflection 1", history.get(0).path("watchouts").get(0).textValue());
        assertEquals("Action for Reflection 1", history.get(0).path("nextActions").get(0).textValue());
        verify(reflectionRepository).findTop7ByUserAndReflectionDateBeforeOrderByReflectionDateDesc(user, selectedDate);
    }

    @Test
    void contextIncludesApplicableActivePlanWithoutAllowingItToBePersistedByReflection() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        CoachingPlan plan = coachingPlan(user, selectedDate.minusDays(10));
        stubInput(user, selectedDate, List.of(), List.of());
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.of(plan));

        JsonNode context = service.getContext(user, selectedDate);

        assertEquals("Improve strength consistently", context.path("activePlan").path("goal").textValue());
        assertEquals("Complete three strength sessions", context.path("activePlan").path("actions").get(0).textValue());
        assertFalse(context.toString().contains("\"id\""));
        verify(coachingPlanRepository).findByUser(user);
    }

    @Test
    void contextOmitsActivePlanBeforeItsStartDate() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        stubInput(user, selectedDate, List.of(), List.of());
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.of(coachingPlan(user, selectedDate.plusDays(1))));

        JsonNode context = service.getContext(user, selectedDate);

        assertFalse(context.has("activePlan"));
    }

    @Test
    void contextTreatsRecordedZeroCaloriesAsValidData() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        CalorieService.DailyCalories calorie = calorie(selectedDate, 0);
        stubInput(user, selectedDate, List.of(), List.of());
        when(calorieService.findBetween(
            user,
            DateTimes.startOfDashboardWeek(selectedDate).minusWeeks(52),
            selectedDate
        )).thenReturn(List.of(calorie));

        JsonNode context = service.getContext(user, selectedDate);

        assertTrue(context.path("dataSemantics").path("recordedZeroCaloriesAreValid").booleanValue());
        assertEquals(0, context.path("calories").get(0).path("calories").intValue());
        JsonNode calorieSummary = context.path("weekProgress").path("currentPeriod").path("calories");
        assertEquals(1, calorieSummary.path("entryCount").intValue());
        assertEquals(0, calorieSummary.path("totalCalories").intValue());
        assertEquals(0, BigDecimal.ZERO.compareTo(calorieSummary.path("averageCalories").decimalValue()));
    }

    @Test
    void contextComparesWeekSoFarWithMatchingPreviousPeriod() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        Mood currentMood = mood(selectedDate, 4, "Current note");
        Mood yearAgoMood = mood(selectedDate.minusWeeks(52), 2, "Year-ago note must not be sent");
        stubInput(user, selectedDate, List.of(yearAgoMood, currentMood), List.of());

        JsonNode context = service.getContext(user, selectedDate);
        JsonNode weekProgress = context.path("weekProgress");

        assertFalse(weekProgress.path("completeWeek").booleanValue());
        assertEquals("2026-07-18", weekProgress.path("currentPeriod").path("startDate").textValue());
        assertEquals("2026-07-20", weekProgress.path("currentPeriod").path("endDate").textValue());
        assertEquals(0, new BigDecimal("4.00").compareTo(weekProgress.path("currentPeriod").path("moodAverage").decimalValue()));
        assertEquals("2026-07-11", weekProgress.path("previousComparablePeriod").path("startDate").textValue());
        assertEquals("2026-07-13", weekProgress.path("previousComparablePeriod").path("endDate").textValue());
        assertEquals("2025-07-19", weekProgress.path("yearAgoComparablePeriod").path("startDate").textValue());
        assertEquals("2025-07-21", weekProgress.path("yearAgoComparablePeriod").path("endDate").textValue());
        assertEquals(0, new BigDecimal("2.00").compareTo(weekProgress.path("yearAgoComparablePeriod").path("moodAverage").decimalValue()));
        assertFalse(context.toString().contains("Year-ago note must not be sent"));
    }

    @Test
    void contextWeightsEachMoodDateOnce() {
        User user = user();
        LocalDate selectedDate = user.getLastCompletedDashboardDate();
        stubInput(user, selectedDate, List.of(
            mood(selectedDate.minusDays(1), MoodPeriod.MORNING, 1, null),
            mood(selectedDate.minusDays(1), MoodPeriod.EVENING, 5, null),
            mood(selectedDate, MoodPeriod.MORNING, 5, null)
        ), List.of());

        JsonNode currentPeriod = service.getContext(user, selectedDate).path("weekProgress").path("currentPeriod");

        assertEquals(0, new BigDecimal("4.00").compareTo(currentPeriod.path("moodAverage").decimalValue()));
    }

    @Test
    void fridayContextComparesCompleteDashboardWeeks() {
        User user = user();
        LocalDate selectedDate = LocalDate.of(2026, 7, 24);
        user.setLastCompletedDashboardDate(selectedDate);
        stubInput(user, selectedDate, List.of(), List.of());

        JsonNode weekProgress = service.getContext(user, selectedDate).path("weekProgress");

        assertTrue(weekProgress.path("completeWeek").booleanValue());
        assertEquals("2026-07-18", weekProgress.path("currentPeriod").path("startDate").textValue());
        assertEquals("2026-07-24", weekProgress.path("currentPeriod").path("endDate").textValue());
        assertEquals("2026-07-11", weekProgress.path("previousComparablePeriod").path("startDate").textValue());
        assertEquals("2026-07-17", weekProgress.path("previousComparablePeriod").path("endDate").textValue());
        assertEquals("2025-07-19", weekProgress.path("yearAgoComparablePeriod").path("startDate").textValue());
        assertEquals("2025-07-25", weekProgress.path("yearAgoComparablePeriod").path("endDate").textValue());
        assertFalse(weekProgress.path("yearAgoComparablePeriod").has("moodAverage"));
    }

    @Test
    void baselineUsesSaturdayToFridayBucketsWithPartialBoundaryPeriods() {
        User user = user();
        LocalDate selectedDate = LocalDate.of(2026, 7, 24);
        user.setLastCompletedDashboardDate(selectedDate);
        stubInput(user, selectedDate, List.of(), List.of());

        JsonNode baselineWeeks = service.getContext(user, selectedDate).path("baselineWeeks");

        assertEquals("2026-04-26", baselineWeeks.get(0).path("startDate").textValue());
        assertEquals("2026-05-01", baselineWeeks.get(0).path("endDate").textValue());
        for (int index = 1; index < baselineWeeks.size() - 1; index++) {
            assertEquals(DayOfWeek.SATURDAY, LocalDate.parse(baselineWeeks.get(index).path("startDate").textValue()).getDayOfWeek());
            assertEquals(DayOfWeek.FRIDAY, LocalDate.parse(baselineWeeks.get(index).path("endDate").textValue()).getDayOfWeek());
        }
        JsonNode lastPeriod = baselineWeeks.get(baselineWeeks.size() - 1);
        assertEquals("2026-06-20", lastPeriod.path("startDate").textValue());
        assertEquals("2026-06-24", lastPeriod.path("endDate").textValue());
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
        WorkoutAssessment assessment = new WorkoutAssessment();
        assessment.setGoalAlignmentScore(8);
        workout.setAssessment(assessment);
        stubInput(user, selectedDate, List.of(), List.of(), List.of(workout), Map.of(routine, checkins));

        JsonNode context = service.getContext(user, selectedDate);
        String json = context.toString();

        assertTrue(json.contains("\"checkinCount\":30"));
        assertTrue(json.contains("\"lastCheckinDate\":\"" + selectedDate + "\""));
        assertFalse(json.contains("checkinDayOffsets"));
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
        assertFalse(json.contains("goalAlignmentScore"));
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
        LocalDate dataStart = DateTimes.startOfDashboardWeek(selectedDate).minusWeeks(52);
        DailyStatus first = status(dataStart.minusDays(1));
        when(dailyStatusRepository.findFirstByUserOrderByStatusDateAsc(user)).thenReturn(Optional.of(first));
        when(dailyStatusRepository.findByUserAndStatusDateBetweenOrderByStatusDateAsc(user, dataStart, selectedDate)).thenReturn(List.of());
        when(weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(dataStart),
            DateTimes.startOfDay(selectedDate).plusDays(1)
        )).thenReturn(weights);
        when(bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(dataStart),
            DateTimes.startOfDay(selectedDate).plusDays(1)
        )).thenReturn(List.of());
        when(moodRepository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(user, dataStart, selectedDate)).thenReturn(moods);
        when(sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, dataStart, selectedDate)).thenReturn(List.of());
        when(calorieService.findBetween(user, dataStart, selectedDate)).thenReturn(List.of());
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, dataStart, selectedDate)).thenReturn(workouts);
        when(sicknessRepository.findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(user, dataStart, selectedDate)).thenReturn(List.of());
        when(decisionOutcomeRepository.findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(user, dataStart, selectedDate)).thenReturn(List.of());
        when(habitRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of());
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(checkins.keySet().stream().toList());
        checkins.forEach((routine, routineCheckins) ->
            when(routineCheckinRepository.findByRoutineAndCheckedAtBetweenOrderByCheckedAtAsc(
                routine,
                DateTimes.startOfDay(dataStart),
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
        reflection.setSummary("Summary for " + title);
        reflection.setPositiveSignals(List.of("Positive for " + title));
        reflection.setWatchouts(List.of("Watchout for " + title));
        reflection.setNextActions(List.of("Action for " + title));
        return reflection;
    }

    private CoachingPlan coachingPlan(User user, LocalDate startDate) {
        CoachingPlan plan = new CoachingPlan();
        plan.setId(20L);
        plan.setUser(user);
        plan.setGoal("Improve strength consistently");
        plan.setPrinciples(List.of("Train without aggravating pain"));
        plan.setPriorities(List.of("Consistency"));
        plan.setActions(List.of("Complete three strength sessions"));
        plan.setStartDate(startDate);
        plan.setUpdatedAt(java.time.Instant.parse("2026-07-15T10:00:00Z"));
        return plan;
    }

    private Mood mood(LocalDate date, int value, String note) {
        return mood(date, MoodPeriod.EVENING, value, note);
    }

    private Mood mood(LocalDate date, MoodPeriod period, int value, String note) {
        Mood mood = new Mood();
        mood.setMoodDate(date);
        mood.setPeriod(period);
        mood.setValue(value);
        mood.setNote(note);
        return mood;
    }

    private CalorieService.DailyCalories calorie(LocalDate date, int calories) {
        return new CalorieService.DailyCalories(date, calories);
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
