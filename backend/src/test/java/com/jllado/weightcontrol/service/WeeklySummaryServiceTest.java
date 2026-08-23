package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WeeklySummaryServiceTest {

    private UserRepository userRepository;
    private WeightRepository weightRepository;
    private BloodPressureRepository bloodPressureRepository;
    private MoodRepository moodRepository;
    private SleepRepository sleepRepository;
    private CalorieService calorieService;
    private WorkoutRepository workoutRepository;
    private DecisionOutcomeRepository decisionOutcomeRepository;
    private RoutineRepository routineRepository;
    private RoutineCheckinRepository routineCheckinRepository;
    private DailyStatusSnapshotService snapshotService;
    private WeeklySummaryMailSender mailSender;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        weightRepository = mock(WeightRepository.class);
        bloodPressureRepository = mock(BloodPressureRepository.class);
        moodRepository = mock(MoodRepository.class);
        sleepRepository = mock(SleepRepository.class);
        calorieService = mock(CalorieService.class);
        workoutRepository = mock(WorkoutRepository.class);
        decisionOutcomeRepository = mock(DecisionOutcomeRepository.class);
        routineRepository = mock(RoutineRepository.class);
        routineCheckinRepository = mock(RoutineCheckinRepository.class);
        snapshotService = mock(DailyStatusSnapshotService.class);
        mailSender = mock(WeeklySummaryMailSender.class);
    }

    @Test
    void latestCompletedWeekAlwaysEndsOnThePreviousFriday() {
        WeeklySummaryService service = service(properties(true));

        assertEquals(LocalDate.of(2026, 8, 14), service.latestCompletedWeekEnd(LocalDate.of(2026, 8, 15)));
        assertEquals(LocalDate.of(2026, 8, 14), service.latestCompletedWeekEnd(LocalDate.of(2026, 8, 16)));
        assertEquals(LocalDate.of(2026, 8, 14), service.latestCompletedWeekEnd(LocalDate.of(2026, 8, 21)));
    }

    @Test
    void progressBuildsCurrentPreviousAndYearAgoSaturdayToFridayWeeks() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        when(snapshotService.getFullWeek(user, LocalDate.of(2025, 8, 15))).thenReturn(statuses(LocalDate.of(2025, 8, 9)));
        when(snapshotService.getFullWeek(user, LocalDate.of(2026, 8, 7))).thenReturn(statuses(LocalDate.of(2026, 8, 1)));
        when(snapshotService.rebuild(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(LocalDate.class)))
            .thenAnswer(invocation -> status(invocation.getArgument(1)));
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of());
        when(weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(moodRepository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(calorieService.findBetween(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(decisionOutcomeRepository.findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        WeeklyMetrics.Progress progress = service(properties(true)).buildProgress(user, end);

        assertEquals(LocalDate.of(2026, 8, 8), progress.currentPeriod().startDate());
        assertEquals(LocalDate.of(2026, 8, 1), progress.previousComparablePeriod().startDate());
        assertEquals(LocalDate.of(2025, 8, 9), progress.yearAgoComparablePeriod().startDate());
        assertEquals(7, progress.currentPeriod().routineCompletion().days().size());
    }

    @Test
    void latestMeasurementsUseCurrentReadingsAndHistoricalPeriodEndSnapshots() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        WeeklyMetrics.Progress progress = new WeeklyMetricsCalculator().progress(user, end, emptyInput());
        Weight currentWeight = weight(LocalDate.of(2026, 8, 16), "70.10");
        Weight previousWeight = weight(LocalDate.of(2026, 7, 29), "68.90");
        Weight yearAgoWeight = weight(LocalDate.of(2025, 7, 1), "72.00");
        BloodPressure currentBloodPressure = bloodPressure(LocalDate.of(2026, 8, 16), 121, 81);
        BloodPressure previousBloodPressure = bloodPressure(LocalDate.of(2026, 7, 30), 126, 84);
        BloodPressure yearAgoBloodPressure = bloodPressure(LocalDate.of(2025, 7, 2), 130, 86);
        when(weightRepository.findFirstByUserOrderByMeasuredAtDesc(user)).thenReturn(Optional.of(currentWeight));
        when(weightRepository.findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(user, DateTimes.startOfDay(LocalDate.of(2026, 8, 8))))
            .thenReturn(Optional.of(previousWeight));
        when(weightRepository.findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(user, DateTimes.startOfDay(LocalDate.of(2025, 8, 16))))
            .thenReturn(Optional.of(yearAgoWeight));
        when(bloodPressureRepository.findFirstByUserOrderByMeasuredAtDesc(user)).thenReturn(Optional.of(currentBloodPressure));
        when(bloodPressureRepository.findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(user, DateTimes.startOfDay(LocalDate.of(2026, 8, 8))))
            .thenReturn(Optional.of(previousBloodPressure));
        when(bloodPressureRepository.findFirstByUserAndMeasuredAtLessThanOrderByMeasuredAtDesc(user, DateTimes.startOfDay(LocalDate.of(2025, 8, 16))))
            .thenReturn(Optional.of(yearAgoBloodPressure));

        WeeklySummaryMeasurements measurements = service(properties(true)).latestMeasurements(user, progress);

        assertEquals(currentWeight, measurements.currentPeriod().weight());
        assertEquals(previousWeight, measurements.previousComparablePeriod().weight());
        assertEquals(yearAgoWeight, measurements.yearAgoComparablePeriod().weight());
        assertEquals(currentBloodPressure, measurements.currentPeriod().bloodPressure());
        assertEquals(previousBloodPressure, measurements.previousComparablePeriod().bloodPressure());
        assertEquals(yearAgoBloodPressure, measurements.yearAgoComparablePeriod().bloodPressure());
    }

    @Test
    void scheduledDeliveryDoesNothingWhenDisabled() {
        WeeklySummaryService service = service(properties(false));
        WeeklySummaryScheduler scheduler = new WeeklySummaryScheduler(userRepository, service, properties(false));

        scheduler.sendScheduledSummary();

        verifyNoInteractions(userRepository, mailSender);
    }

    private WeeklySummaryService service(AppProperties properties) {
        return new WeeklySummaryService(
            weightRepository,
            bloodPressureRepository,
            moodRepository,
            sleepRepository,
            calorieService,
            workoutRepository,
            decisionOutcomeRepository,
            routineRepository,
            routineCheckinRepository,
            snapshotService,
            new WeeklyMetricsCalculator(),
            mailSender,
            properties
        );
    }

    private List<DailyStatus> statuses(LocalDate start) {
        return start.datesUntil(start.plusDays(7)).map(this::status).toList();
    }

    private WeeklyMetricsCalculator.Input emptyInput() {
        return new WeeklyMetricsCalculator.Input(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private Weight weight(LocalDate date, String value) {
        Weight weight = new Weight();
        weight.setMeasuredAt(DateTimes.startOfDay(date).plusHours(8));
        weight.setWeight(new BigDecimal(value));
        return weight;
    }

    private BloodPressure bloodPressure(LocalDate date, int upper, int lower) {
        BloodPressure bloodPressure = new BloodPressure();
        bloodPressure.setMeasuredAt(DateTimes.startOfDay(date).plusHours(9));
        bloodPressure.setUpper(upper);
        bloodPressure.setLower(lower);
        return bloodPressure;
    }

    private DailyStatus status(LocalDate date) {
        DailyStatus status = new DailyStatus();
        status.setStatusDate(date);
        status.setRoutinesDone(1);
        status.setTotalRoutines(2);
        status.setRoutinesPercentage(new BigDecimal("50.00"));
        status.setWeightPercentage(BigDecimal.ZERO);
        status.setBloodPressurePercentage(BigDecimal.ZERO);
        status.setFlexibilityPercentage(BigDecimal.ZERO);
        status.setMindPercentage(BigDecimal.ZERO);
        return status;
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setEmail("owner@example.com");
        user.setTypicalCaloriesSaturday(2000);
        user.setTypicalCaloriesSunday(2000);
        user.setTypicalCaloriesMonday(2000);
        user.setTypicalCaloriesTuesday(2000);
        user.setTypicalCaloriesWednesday(2000);
        user.setTypicalCaloriesThursday(2000);
        user.setTypicalCaloriesFriday(2000);
        return user;
    }

    private AppProperties properties(boolean enabled) {
        return new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("", "owner@example.com", "https://test.example", "test-file-signing-secret-32-bytes-long"),
            new AppProperties.Push(false, "", "", "", ""),
            new AppProperties.WeeklySummary(enabled, "owner@example.com", "owner@example.com", "sender@example.com", "https://weight.example")
        );
    }
}
