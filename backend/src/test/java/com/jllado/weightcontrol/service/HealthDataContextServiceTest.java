package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachCatalogResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachContextResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.ActivePlanContext;
import com.jllado.weightcontrol.api.dto.CoachDtos.DomainAvailability;
import com.jllado.weightcontrol.api.dto.CoachDtos.HealthEventsContext;
import com.jllado.weightcontrol.api.dto.CoachDtos.HealthConstraintsContext;
import com.jllado.weightcontrol.api.dto.CoachDtos.NutritionContext;
import com.jllado.weightcontrol.api.dto.CoachDtos.TrainingContext;
import com.jllado.weightcontrol.api.dto.CoachDtos.VitalsContext;
import com.jllado.weightcontrol.api.dto.ProgressPhotoDtos.ProgressPhotoSetResponse;
import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.HealthConstraintSource;
import com.jllado.weightcontrol.domain.HealthConstraintType;
import com.jllado.weightcontrol.domain.LipidPanel;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealSource;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.SicknessSeverity;
import com.jllado.weightcontrol.domain.SicknessType;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthDataContextServiceTest {

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
    private DecisionOutcomeService decisionOutcomeService;
    @Mock
    private ProgressPhotoService progressPhotoService;

    private HealthDataContextService service;

    @BeforeEach
    void setUp() {
        service = new HealthDataContextService(
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
            progressPhotoService
        );
    }

    @Test
    void catalogReportsDomainCountsAndCoverageWithoutHealthRecords() {
        User user = user();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-16T10:15:00+02:00");
        Weight firstWeight = weight(LocalDate.of(2026, 1, 2));
        Weight lastWeight = weight(LocalDate.of(2026, 8, 16));
        firstWeight.setPhotoFrontPath("private/first.jpg");
        lastWeight.setPhotoLeftPath("private/last.jpg");
        Mood firstMood = mood(LocalDate.of(2026, 2, 1));
        Mood lastMood = mood(LocalDate.of(2026, 8, 15));
        Sleep firstSleep = sleep(LocalDate.of(2026, 1, 1));
        Sleep lastSleep = sleep(LocalDate.of(2026, 8, 14));
        Sickness sickness = sickness(LocalDate.of(2026, 3, 4));
        BackPainEpisode backPain = backPain(LocalDate.of(2026, 8, 16));
        BloodPressure bloodPressure = bloodPressure(LocalDate.of(2026, 8, 16));
        LipidPanel firstLipidPanel = lipidPanel(LocalDate.of(2021, 9, 4));
        LipidPanel lastLipidPanel = lipidPanel(LocalDate.of(2026, 2, 2));
        HealthConstraint firstConstraint = healthConstraint(user, LocalDate.of(2025, 10, 1));
        HealthConstraint lastConstraint = healthConstraint(user, LocalDate.of(2026, 8, 1));
        CoachingPlan plan = coachingPlan(user, LocalDate.of(2026, 8, 10));
        when(mealService.count(user)).thenReturn(2L);
        when(mealService.findFirstRecordedDate(user)).thenReturn(Optional.of(LocalDate.of(2026, 2, 1)));
        when(mealService.findLastRecordedDate(user)).thenReturn(Optional.of(LocalDate.of(2026, 8, 15)));
        when(fastingPeriodService.count(user)).thenReturn(1L);
        when(fastingPeriodService.findFirstRecordedDate(user)).thenReturn(Optional.of(LocalDate.of(2026, 1, 31)));
        when(fastingPeriodService.findLastRecordedDate(user)).thenReturn(Optional.of(LocalDate.of(2026, 8, 16)));
        when(weightRepository.countByUser(user)).thenReturn(2L);
        when(weightRepository.findFirstByUserOrderByMeasuredAtAsc(user)).thenReturn(Optional.of(firstWeight));
        when(weightRepository.findFirstByUserOrderByMeasuredAtDesc(user)).thenReturn(Optional.of(lastWeight));
        when(moodRepository.countByUser(user)).thenReturn(2L);
        when(moodRepository.findFirstByUserOrderByMoodDateAsc(user)).thenReturn(Optional.of(firstMood));
        when(moodRepository.findFirstByUserOrderByMoodDateDesc(user)).thenReturn(Optional.of(lastMood));
        when(sleepRepository.countByUser(user)).thenReturn(2L);
        when(sleepRepository.findFirstByUserOrderBySleepDateAsc(user)).thenReturn(Optional.of(firstSleep));
        when(sleepRepository.findFirstByUserOrderBySleepDateDesc(user)).thenReturn(Optional.of(lastSleep));
        when(sicknessRepository.countByUser(user)).thenReturn(1L);
        when(sicknessRepository.findFirstByUserOrderBySicknessDateAsc(user)).thenReturn(Optional.of(sickness));
        when(sicknessRepository.findFirstByUserOrderBySicknessDateDesc(user)).thenReturn(Optional.of(sickness));
        when(backPainEpisodeRepository.countByUser(user)).thenReturn(1L);
        when(backPainEpisodeRepository.findFirstByUserOrderByEpisodeDateAscEpisodeTimeAscIdAsc(user))
            .thenReturn(Optional.of(backPain));
        when(backPainEpisodeRepository.findFirstByUserOrderByEpisodeDateDescEpisodeTimeDescIdDesc(user))
            .thenReturn(Optional.of(backPain));
        when(bloodPressureRepository.countByUser(user)).thenReturn(1L);
        when(bloodPressureRepository.findFirstByUserOrderByMeasuredAtAsc(user)).thenReturn(Optional.of(bloodPressure));
        when(bloodPressureRepository.findFirstByUserOrderByMeasuredAtDesc(user)).thenReturn(Optional.of(bloodPressure));
        when(lipidPanelRepository.countByUser(user)).thenReturn(2L);
        when(lipidPanelRepository.findFirstByUserOrderByPanelDateAsc(user)).thenReturn(Optional.of(firstLipidPanel));
        when(lipidPanelRepository.findFirstByUserOrderByPanelDateDesc(user)).thenReturn(Optional.of(lastLipidPanel));
        when(healthConstraintRepository.countByUser(user)).thenReturn(2L);
        when(healthConstraintRepository.findFirstByUserOrderByStartDateAscIdAsc(user))
            .thenReturn(Optional.of(firstConstraint));
        when(healthConstraintRepository.findFirstByUserOrderByStartDateDescIdDesc(user))
            .thenReturn(Optional.of(lastConstraint));
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.of(plan));
        when(progressPhotoService.findAll(user)).thenReturn(List.of(
            ProgressPhotoSetResponse.from(lastWeight),
            ProgressPhotoSetResponse.from(firstWeight)
        ));

        CoachCatalogResponse response = service.getCoachCatalog(user, now);
        Map<CoachDomain, DomainAvailability> domains = response.domains().stream()
            .collect(Collectors.toMap(DomainAvailability::domain, Function.identity()));

        assertEquals(DateTimes.USER_ZONE.getId(), response.timezone());
        assertEquals(now, response.currentLocalDateTime());
        assertEquals(user.getLastCompletedDashboardDate(), response.lastCompletedDate());
        assertEquals(13, domains.size());
        assertEquals(1, domains.get(CoachDomain.PROFILE).recordCount());
        assertNull(domains.get(CoachDomain.PROFILE).firstDate());
        assertEquals(2, domains.get(CoachDomain.BODY).recordCount());
        assertEquals(LocalDate.of(2026, 1, 2), domains.get(CoachDomain.BODY).firstDate());
        assertEquals(LocalDate.of(2026, 8, 16), domains.get(CoachDomain.BODY).lastDate());
        assertEquals(4, domains.get(CoachDomain.RECOVERY).recordCount());
        assertEquals(LocalDate.of(2026, 1, 1), domains.get(CoachDomain.RECOVERY).firstDate());
        assertEquals(LocalDate.of(2026, 8, 15), domains.get(CoachDomain.RECOVERY).lastDate());
        assertEquals(2, domains.get(CoachDomain.HEALTH_EVENTS).recordCount());
        assertEquals(LocalDate.of(2026, 3, 4), domains.get(CoachDomain.HEALTH_EVENTS).firstDate());
        assertEquals(LocalDate.of(2026, 8, 16), domains.get(CoachDomain.HEALTH_EVENTS).lastDate());
        assertEquals(3, domains.get(CoachDomain.VITALS).recordCount());
        assertEquals(LocalDate.of(2021, 9, 4), domains.get(CoachDomain.VITALS).firstDate());
        assertEquals(LocalDate.of(2026, 8, 16), domains.get(CoachDomain.VITALS).lastDate());
        assertEquals(3, domains.get(CoachDomain.NUTRITION).recordCount());
        assertEquals(LocalDate.of(2026, 1, 31), domains.get(CoachDomain.NUTRITION).firstDate());
        assertEquals(LocalDate.of(2026, 8, 16), domains.get(CoachDomain.NUTRITION).lastDate());
        assertEquals(2, domains.get(CoachDomain.HEALTH_CONSTRAINTS).recordCount());
        assertEquals(LocalDate.of(2025, 10, 1), domains.get(CoachDomain.HEALTH_CONSTRAINTS).firstDate());
        assertEquals(LocalDate.of(2026, 8, 1), domains.get(CoachDomain.HEALTH_CONSTRAINTS).lastDate());
        assertEquals(1, domains.get(CoachDomain.ACTIVE_PLAN).recordCount());
        assertEquals(plan.getStartDate(), domains.get(CoachDomain.ACTIVE_PLAN).firstDate());
        assertEquals(plan.getStartDate(), domains.get(CoachDomain.ACTIVE_PLAN).lastDate());
        assertEquals(2, domains.get(CoachDomain.PROGRESS_PHOTOS).recordCount());
        assertEquals(LocalDate.of(2026, 1, 2), domains.get(CoachDomain.PROGRESS_PHOTOS).firstDate());
        assertEquals(LocalDate.of(2026, 8, 16), domains.get(CoachDomain.PROGRESS_PHOTOS).lastDate());
    }

    @Test
    void contextReturnsOnlyRequestedDomainsAndIncludesPartialDataFromToday() throws Exception {
        User user = user();
        LocalDate today = LocalDate.of(2026, 8, 16);
        OffsetDateTime now = OffsetDateTime.parse("2026-08-16T10:15:00+02:00");
        BackPainEpisode backPain = backPain(today);
        BackPainEpisode secondBackPain = backPain(today);
        secondBackPain.setId(11L);
        secondBackPain.setRegion(BackRegion.UPPER);
        secondBackPain.setSide(BackSide.RIGHT);
        secondBackPain.setSeverity(BackPainSeverity.SEVERE);
        Meal meal = new Meal();
        meal.setId(99L);
        meal.setUser(user);
        meal.setMealDate(today);
        meal.setMealType(MealType.BREAKFAST);
        meal.setMealSequence(1);
        meal.setMealTime(LocalTime.of(9, 0));
        meal.setCalories(0);
        meal.setProteinGrams(new BigDecimal("20"));
        meal.setNotes("Recorded breakfast");
        meal.setSource(MealSource.MANUAL);
        FastingPeriod fastingPeriod = new FastingPeriod();
        fastingPeriod.setId(100L);
        fastingPeriod.setUser(user);
        fastingPeriod.setStartTime(now.minusHours(16));
        fastingPeriod.setEndTime(now);
        fastingPeriod.setNotes("Overnight fast");
        when(nutritionService.findBetween(user, today, today)).thenReturn(List.of(
            new NutritionService.DailyNutritionSummary(today, 0, new BigDecimal("20"), null, null, false)
        ));
        when(mealService.findBetween(user, today, today)).thenReturn(List.of(meal));
        when(fastingPeriodService.findBetween(user, today, today)).thenReturn(List.of(fastingPeriod));
        when(sicknessRepository.findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(user, today, today))
            .thenReturn(List.of());
        when(backPainEpisodeRepository.findByUserAndEpisodeDateBetweenOrderByEpisodeDateAscEpisodeTimeAscIdAsc(user, today, today))
            .thenReturn(List.of(backPain, secondBackPain));

        CoachContextResponse response = service.getHealthContext(
            user,
            today,
            today,
            Set.of(CoachDomain.NUTRITION, CoachDomain.HEALTH_EVENTS),
            now
        );

        assertEquals(List.of(CoachDomain.NUTRITION, CoachDomain.HEALTH_EVENTS), response.data().keySet().stream().toList());
        assertFalse(response.endDateComplete());
        assertTrue(response.dataSemantics().absentRecordsAreUnknown());
        assertTrue(response.dataSemantics().recordedZeroCaloriesAreValid());
        NutritionContext nutrition = (NutritionContext) response.data().get(CoachDomain.NUTRITION);
        HealthEventsContext healthEvents = (HealthEventsContext) response.data().get(CoachDomain.HEALTH_EVENTS);
        assertEquals(0, nutrition.dailyTotals().getFirst().calories());
        assertFalse(nutrition.dailyTotals().getFirst().macrosComplete());
        assertEquals(MealType.BREAKFAST, nutrition.meals().getFirst().mealType());
        assertEquals("Overnight fast", nutrition.fastingPeriods().getFirst().notes());
        assertEquals(2, healthEvents.backPainEpisodes().size());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertTrue(json.contains("\"calories\":0"));
        assertTrue(json.contains("\"macrosComplete\":false"));
        assertTrue(json.contains("\"source\":\"MANUAL\""));
        assertTrue(json.contains("\"fastingPeriods\""));
        assertTrue(json.contains("\"backPainEpisodes\""));
        assertTrue(json.contains("\"severity\":\"MODERATE\""));
        assertTrue(json.contains("\"severity\":\"SEVERE\""));
        assertFalse(json.contains("private@example.com"));
        assertFalse(json.contains("photoFrontPath"));
        assertFalse(json.contains("\"id\""));
        verifyNoInteractions(weightRepository, bloodPressureRepository, lipidPanelRepository, moodRepository, sleepRepository, workoutRepository);
    }

    @Test
    void contextExposesLipidPanelsInsideVitalsWithoutPrivateFields() throws Exception {
        User user = user();
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        LipidPanel panel = lipidPanel(LocalDate.of(2026, 2, 2));
        panel.setId(99L);
        panel.setUser(user);
        when(bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(from),
            DateTimes.startOfDay(to).plusDays(1)
        )).thenReturn(List.of());
        when(lipidPanelRepository.findByUserAndPanelDateBetweenOrderByPanelDateAsc(user, from, to)).thenReturn(List.of(panel));

        CoachContextResponse response = service.getHealthContext(
            user,
            from,
            to,
            Set.of(CoachDomain.VITALS),
            OffsetDateTime.parse("2026-08-16T10:15:00+02:00")
        );
        VitalsContext vitals = (VitalsContext) response.data().get(CoachDomain.VITALS);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertTrue(vitals.bloodPressures().isEmpty());
        assertEquals(1, vitals.lipidPanels().size());
        assertEquals(211, vitals.lipidPanels().getFirst().totalCholesterol());
        assertEquals(211, new ObjectMapper().readTree(json).path("data").path("VITALS").path("lipidPanels").get(0).path("totalCholesterol").intValue());
        assertFalse(json.contains("private@example.com"));
        assertFalse(json.contains("\"id\""));
    }

    @Test
    void contextExposesBodyMassValuesWithoutPrivateWeightFields() throws Exception {
        User user = user();
        LocalDate date = LocalDate.of(2026, 8, 16);
        Weight weight = weight(date);
        weight.setId(99L);
        weight.setFat(new BigDecimal("20.50"));
        weight.setMuscle(new BigDecimal("55.25"));
        weight.setPhotoFrontPath("/private/front.jpg");
        when(weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date).plusDays(1)
        )).thenReturn(List.of(weight));

        CoachContextResponse response = service.getHealthContext(
            user,
            date,
            date,
            Set.of(CoachDomain.BODY),
            OffsetDateTime.parse("2026-08-16T10:15:00+02:00")
        );
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertTrue(json.contains("\"fatMassKg\":20.50"));
        assertTrue(json.contains("\"muscleMassKg\":55.25"));
        assertFalse(json.contains("private@example.com"));
        assertFalse(json.contains("/private/front.jpg"));
        assertFalse(json.contains("\"id\""));
    }

    @Test
    void coachTrainingContextIncludesAssessmentSummaryWithoutIdentifiers() throws Exception {
        User user = user();
        LocalDate date = LocalDate.of(2026, 8, 20);
        Workout workout = assessedWorkout(user, date);
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, date, date))
            .thenReturn(List.of(workout));

        CoachContextResponse response = service.getHealthContext(
            user,
            date,
            date,
            Set.of(CoachDomain.TRAINING),
            OffsetDateTime.parse("2026-08-20T20:00:00+02:00")
        );
        TrainingContext training = (TrainingContext) response.data().get(CoachDomain.TRAINING);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertEquals(8, training.days().getFirst().assessment().goalAlignmentScore());
        assertEquals("Improve upper-body strength", training.days().getFirst().assessment().goalSnapshot());
        assertFalse(training.days().getFirst().assessment().outdated());
        assertFalse(json.contains("workoutUpdatedAt"));
        assertFalse(json.contains("planUpdatedAt"));
        assertFalse(json.contains("\"id\""));
    }

    @Test
    void contextReturnsRequestedEmptyDomain() {
        User user = user();
        LocalDate date = LocalDate.of(2026, 8, 16);
        when(bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date).plusDays(1)
        )).thenReturn(List.of());
        when(lipidPanelRepository.findByUserAndPanelDateBetweenOrderByPanelDateAsc(user, date, date)).thenReturn(List.of());

        CoachContextResponse response = service.getHealthContext(
            user,
            date,
            date,
            Set.of(CoachDomain.VITALS),
            OffsetDateTime.parse("2026-08-16T10:15:00+02:00")
        );

        assertEquals(1, response.data().size());
        assertTrue(response.data().containsKey(CoachDomain.VITALS));
    }

    @Test
    void contextReturnsActiveOverlappingConstraintsForIncompleteTodayWithoutPrivateFields() throws Exception {
        User user = user();
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate today = LocalDate.of(2026, 8, 16);
        HealthConstraint constraint = healthConstraint(user, LocalDate.of(2026, 7, 1));
        constraint.setId(99L);
        constraint.setEndDate(today);
        when(healthConstraintRepository.findActiveOverlapping(user, from, today)).thenReturn(List.of(constraint));

        CoachContextResponse response = service.getHealthContext(
            user,
            from,
            today,
            Set.of(CoachDomain.HEALTH_CONSTRAINTS),
            OffsetDateTime.parse("2026-08-16T10:15:00+02:00")
        );
        HealthConstraintsContext context = (HealthConstraintsContext) response.data()
            .get(CoachDomain.HEALTH_CONSTRAINTS);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertFalse(response.endDateComplete());
        assertEquals(1, context.constraints().size());
        assertEquals(HealthConstraintSource.PHYSIOTHERAPIST, context.constraints().getFirst().source());
        assertTrue(json.contains("\"title\":\"Prescribed core exercises\""));
        assertFalse(json.contains("private@example.com"));
        assertFalse(json.contains("\"id\""));
        assertFalse(json.contains("createdAt"));
        assertFalse(json.contains("updatedAt"));
        assertFalse(json.contains("\"active\""));
    }

    @Test
    void contextReturnsTheCompleteActivePlanWithoutPrivateFields() throws Exception {
        User user = user();
        LocalDate date = LocalDate.of(2026, 8, 16);
        CoachingPlan plan = coachingPlan(user, LocalDate.of(2026, 8, 10));
        plan.setId(99L);
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.of(plan));

        CoachContextResponse response = service.getHealthContext(
            user,
            date,
            date,
            Set.of(CoachDomain.ACTIVE_PLAN),
            OffsetDateTime.parse("2026-08-16T10:15:00+02:00")
        );
        ActivePlanContext context = (ActivePlanContext) response.data().get(CoachDomain.ACTIVE_PLAN);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertEquals("Improve strength consistently", context.plan().goal());
        assertEquals(List.of("Train without aggravating pain"), context.plan().principles());
        assertEquals(List.of("Complete three strength sessions"), context.plan().actions());
        assertTrue(json.contains("\"ACTIVE_PLAN\""));
        assertFalse(json.contains("private@example.com"));
        assertFalse(json.contains("\"id\""));
        assertFalse(json.contains("createdAt"));
    }

    @Test
    void contextAcceptsNinetyInclusiveDays() {
        User user = user();
        LocalDate to = LocalDate.of(2026, 8, 16);

        service.getHealthContext(
            user,
            to.minusDays(89),
            to,
            Set.of(CoachDomain.PROFILE),
            OffsetDateTime.parse("2026-08-16T10:15:00+02:00")
        );
    }

    @Test
    void contextRejectsInvalidRanges() {
        User user = user();
        LocalDate today = LocalDate.of(2026, 8, 16);
        OffsetDateTime now = OffsetDateTime.parse("2026-08-16T10:15:00+02:00");

        assertThrows(BadRequestException.class, () -> service.getHealthContext(
            user, today, today, Set.of(), now
        ));
        assertThrows(BadRequestException.class, () -> service.getHealthContext(
            user, today, today.minusDays(1), Set.of(CoachDomain.PROFILE), now
        ));
        assertThrows(BadRequestException.class, () -> service.getHealthContext(
            user, today.minusDays(90), today, Set.of(CoachDomain.PROFILE), now
        ));
        assertThrows(BadRequestException.class, () -> service.getHealthContext(
            user, today, today.plusDays(1), Set.of(CoachDomain.PROFILE), now
        ));
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
        user.setLastCompletedDashboardDate(LocalDate.of(2026, 8, 15));
        return user;
    }

    private Weight weight(LocalDate date) {
        Weight weight = new Weight();
        weight.setUser(user());
        weight.setMeasuredAt(DateTimes.startOfDay(date).plusHours(8));
        weight.setWeight(new BigDecimal("80.00"));
        weight.setFatPercentage(new BigDecimal("25.00"));
        weight.setMusclePercentage(new BigDecimal("69.00"));
        weight.setLostWeight(BigDecimal.ZERO);
        weight.setLostFat(BigDecimal.ZERO);
        weight.setLostMuscle(BigDecimal.ZERO);
        return weight;
    }

    private BloodPressure bloodPressure(LocalDate date) {
        BloodPressure bloodPressure = new BloodPressure();
        bloodPressure.setMeasuredAt(DateTimes.startOfDay(date).plusHours(8));
        return bloodPressure;
    }

    private LipidPanel lipidPanel(LocalDate date) {
        LipidPanel panel = new LipidPanel();
        panel.setPanelDate(date);
        panel.setTotalCholesterol(211);
        panel.setHdlCholesterol(63);
        panel.setLdlCholesterol(133);
        panel.setTriglycerides(77);
        return panel;
    }

    private Mood mood(LocalDate date) {
        Mood mood = new Mood();
        mood.setMoodDate(date);
        return mood;
    }

    private Sleep sleep(LocalDate date) {
        Sleep sleep = new Sleep();
        sleep.setSleepDate(date);
        return sleep;
    }

    private Sickness sickness(LocalDate date) {
        Sickness sickness = new Sickness();
        sickness.setSicknessDate(date);
        sickness.setType(SicknessType.COLD);
        sickness.setSeverity(SicknessSeverity.LOW);
        return sickness;
    }

    private BackPainEpisode backPain(LocalDate date) {
        BackPainEpisode episode = new BackPainEpisode();
        episode.setId(10L);
        episode.setUser(user());
        episode.setEpisodeDate(date);
        episode.setEpisodeTime(LocalTime.of(9, 30));
        episode.setPeriod(MoodPeriod.MORNING);
        episode.setRegion(BackRegion.LOWER);
        episode.setSide(BackSide.CENTER);
        episode.setSeverity(BackPainSeverity.MODERATE);
        episode.setNote("After sitting");
        return episode;
    }

    private HealthConstraint healthConstraint(User user, LocalDate startDate) {
        HealthConstraint constraint = new HealthConstraint();
        constraint.setUser(user);
        constraint.setType(HealthConstraintType.CLINICIAN_GUIDANCE);
        constraint.setTitle("Prescribed core exercises");
        constraint.setDetails("Bird dogs and side planks three times per week");
        constraint.setSource(HealthConstraintSource.PHYSIOTHERAPIST);
        constraint.setStartDate(startDate);
        constraint.setActive(true);
        return constraint;
    }

    private CoachingPlan coachingPlan(User user, LocalDate startDate) {
        CoachingPlan plan = new CoachingPlan();
        plan.setUser(user);
        plan.setGoal("Improve strength consistently");
        plan.setPrinciples(List.of("Train without aggravating pain"));
        plan.setPriorities(List.of("Consistency", "Recovery"));
        plan.setActions(List.of("Complete three strength sessions"));
        plan.setStartDate(startDate);
        plan.setReviewDate(startDate.plusMonths(1));
        plan.setNotes("Review training tolerance");
        plan.setUpdatedAt(java.time.Instant.parse("2026-08-15T10:00:00Z"));
        return plan;
    }

    private Workout assessedWorkout(User user, LocalDate date) {
        java.time.Instant timestamp = java.time.Instant.parse("2026-08-20T18:00:00Z");
        Exercise exercise = new Exercise();
        exercise.setId(10L);
        exercise.setName("Bench press");
        exercise.setDescription("Horizontal press");
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        WorkoutSegment segment = new WorkoutSegment();
        segment.setPosition(0);
        segment.setRepetitions(8);
        segment.setWeight(new BigDecimal("60.00"));
        WorkoutLine line = new WorkoutLine();
        line.setPosition(0);
        line.setExercise(exercise);
        line.setSegments(List.of(segment));
        Workout workout = new Workout();
        workout.setId(20L);
        workout.setUser(user);
        workout.setWorkoutDate(date);
        workout.setUpdatedAt(timestamp);
        workout.setLines(List.of(line));
        WorkoutAssessment assessment = new WorkoutAssessment();
        assessment.setWorkout(workout);
        assessment.setGoalAlignmentScore(8);
        assessment.setEstimatedTrainingDemandScore(7);
        assessment.setRationale("Clear alignment with the active goal.");
        assessment.setStrength("Consistent compound work.");
        assessment.setImprovement("Add one pulling set.");
        assessment.setNextWorkoutAction("Repeat with controlled progression.");
        assessment.setGoalSnapshot("Improve upper-body strength");
        assessment.setWorkoutUpdatedAt(timestamp);
        workout.setAssessment(assessment);
        return workout;
    }
}
