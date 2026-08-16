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
import com.jllado.weightcontrol.api.dto.CoachDtos.DomainAvailability;
import com.jllado.weightcontrol.api.dto.CoachDtos.NutritionContext;
import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.SicknessSeverity;
import com.jllado.weightcontrol.domain.SicknessType;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
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
    private MoodRepository moodRepository;
    @Mock
    private SleepRepository sleepRepository;
    @Mock
    private CalorieService calorieService;
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
    private RoutineRepository routineRepository;
    @Mock
    private RoutineCheckinRepository routineCheckinRepository;
    @Mock
    private DecisionOutcomeService decisionOutcomeService;

    private HealthDataContextService service;

    @BeforeEach
    void setUp() {
        service = new HealthDataContextService(
            reflectionRepository,
            dailyStatusRepository,
            weightRepository,
            bloodPressureRepository,
            moodRepository,
            sleepRepository,
            calorieService,
            workoutRepository,
            sicknessRepository,
            backPainEpisodeRepository,
            decisionOutcomeRepository,
            habitRepository,
            routineRepository,
            routineCheckinRepository,
            decisionOutcomeService,
            new WeeklyMetricsCalculator()
        );
    }

    @Test
    void catalogReportsDomainCountsAndCoverageWithoutHealthRecords() {
        User user = user();
        OffsetDateTime now = OffsetDateTime.parse("2026-08-16T10:15:00+02:00");
        Weight firstWeight = weight(LocalDate.of(2026, 1, 2));
        Weight lastWeight = weight(LocalDate.of(2026, 8, 16));
        Mood firstMood = mood(LocalDate.of(2026, 2, 1));
        Mood lastMood = mood(LocalDate.of(2026, 8, 15));
        Sleep firstSleep = sleep(LocalDate.of(2026, 1, 1));
        Sleep lastSleep = sleep(LocalDate.of(2026, 8, 14));
        Sickness sickness = sickness(LocalDate.of(2026, 3, 4));
        BackPainEpisode backPain = backPain(LocalDate.of(2026, 8, 16));
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

        CoachCatalogResponse response = service.getCoachCatalog(user, now);
        Map<CoachDomain, DomainAvailability> domains = response.domains().stream()
            .collect(Collectors.toMap(DomainAvailability::domain, Function.identity()));

        assertEquals(DateTimes.USER_ZONE.getId(), response.timezone());
        assertEquals(now, response.currentLocalDateTime());
        assertEquals(user.getLastCompletedDashboardDate(), response.lastCompletedDate());
        assertEquals(10, domains.size());
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
        assertEquals(0, domains.get(CoachDomain.VITALS).recordCount());
        assertNull(domains.get(CoachDomain.VITALS).firstDate());
        assertNull(domains.get(CoachDomain.VITALS).lastDate());
    }

    @Test
    void contextReturnsOnlyRequestedDomainsAndIncludesPartialDataFromToday() throws Exception {
        User user = user();
        LocalDate today = LocalDate.of(2026, 8, 16);
        OffsetDateTime now = OffsetDateTime.parse("2026-08-16T10:15:00+02:00");
        BackPainEpisode backPain = backPain(today);
        when(calorieService.findBetween(user, today, today)).thenReturn(List.of(new CalorieService.DailyCalories(today, 0)));
        when(sicknessRepository.findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(user, today, today))
            .thenReturn(List.of());
        when(backPainEpisodeRepository.findByUserAndEpisodeDateBetweenOrderByEpisodeDateAscEpisodeTimeAscIdAsc(user, today, today))
            .thenReturn(List.of(backPain));

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
        assertEquals(0, nutrition.dailyTotals().getFirst().calories());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertTrue(json.contains("\"calories\":0"));
        assertTrue(json.contains("\"backPainEpisodes\""));
        assertTrue(json.contains("\"severity\":\"MODERATE\""));
        assertFalse(json.contains("private@example.com"));
        assertFalse(json.contains("photoFrontPath"));
        assertFalse(json.contains("\"id\""));
        verifyNoInteractions(weightRepository, bloodPressureRepository, moodRepository, sleepRepository, workoutRepository);
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
    void contextReturnsRequestedEmptyDomain() {
        User user = user();
        LocalDate date = LocalDate.of(2026, 8, 16);
        when(bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
            user,
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date).plusDays(1)
        )).thenReturn(List.of());

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
}
