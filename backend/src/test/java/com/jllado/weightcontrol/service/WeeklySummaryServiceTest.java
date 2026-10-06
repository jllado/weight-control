package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.SicknessSeverity;
import com.jllado.weightcontrol.domain.SicknessType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.CoachingPlanRepository;
import com.jllado.weightcontrol.repository.DecisionOutcomeRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.SicknessRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeeklyReflectionRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.BackPainSummary;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class WeeklySummaryServiceTest {

    private UserRepository userRepository;
    private WeightRepository weightRepository;
    private BloodPressureRepository bloodPressureRepository;
    private MoodRepository moodRepository;
    private SleepRepository sleepRepository;
    private SicknessRepository sicknessRepository;
    private BackPainEpisodeRepository backPainEpisodeRepository;
    private CalorieService calorieService;
    private WorkoutRepository workoutRepository;
    private DecisionOutcomeRepository decisionOutcomeRepository;
    private RoutineRepository routineRepository;
    private RoutineCheckinRepository routineCheckinRepository;
    private DailyStatusSnapshotService snapshotService;
    private PersonalRecordService personalRecordService;
    private WeeklySummaryMailSender mailSender;
    private SavedWeeklySummaryRepository savedSummaryRepository;
    private WeeklyReflectionRepository weeklyReflectionRepository;
    private CoachingPlanRepository coachingPlanRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        weightRepository = mock(WeightRepository.class);
        bloodPressureRepository = mock(BloodPressureRepository.class);
        moodRepository = mock(MoodRepository.class);
        sleepRepository = mock(SleepRepository.class);
        sicknessRepository = mock(SicknessRepository.class);
        backPainEpisodeRepository = mock(BackPainEpisodeRepository.class);
        calorieService = mock(CalorieService.class);
        workoutRepository = mock(WorkoutRepository.class);
        decisionOutcomeRepository = mock(DecisionOutcomeRepository.class);
        routineRepository = mock(RoutineRepository.class);
        routineCheckinRepository = mock(RoutineCheckinRepository.class);
        snapshotService = mock(DailyStatusSnapshotService.class);
        personalRecordService = mock(PersonalRecordService.class);
        mailSender = mock(WeeklySummaryMailSender.class);
        savedSummaryRepository = mock(SavedWeeklySummaryRepository.class);
        weeklyReflectionRepository = mock(WeeklyReflectionRepository.class);
        coachingPlanRepository = mock(CoachingPlanRepository.class);
    }

    @Test
    void latestCompletedWeekAlwaysEndsOnThePreviousFriday() {
        WeeklySummaryService service = service(properties(true));

        assertEquals(LocalDate.of(2026, 8, 14), service.latestCompletedWeekEnd(LocalDate.of(2026, 8, 15)));
        assertEquals(LocalDate.of(2026, 8, 14), service.latestCompletedWeekEnd(LocalDate.of(2026, 8, 16)));
        assertEquals(LocalDate.of(2026, 8, 14), service.latestCompletedWeekEnd(LocalDate.of(2026, 8, 21)));
    }

    @Test
    void outcomeWeekClosesOnlyAfterSundayInMadrid() {
        WeeklySummaryService service = service(properties(true));

        assertEquals(LocalDate.of(2026, 8, 7), service.latestClosedOutcomeWeekEnd(LocalDate.of(2026, 8, 15)));
        assertEquals(LocalDate.of(2026, 8, 7), service.latestClosedOutcomeWeekEnd(LocalDate.of(2026, 8, 16)));
        assertEquals(LocalDate.of(2026, 8, 14), service.latestClosedOutcomeWeekEnd(LocalDate.of(2026, 8, 17)));
    }

    @Test
    void backPainSummaryCountsCheckInsEpisodesDaysSeverityAndLocations() {
        User user = user();
        LocalDate start = LocalDate.of(2026, 9, 19);
        LocalDate end = start.plusDays(6);
        when(backPainEpisodeRepository.findByUserAndEpisodeDateBetweenOrderByEpisodeDateAscEpisodeTimeAscIdAsc(user, start, end))
            .thenReturn(List.of(
                backPainEpisode(start, BackPainSeverity.MILD, BackRegion.LOWER, BackSide.LEFT),
                backPainEpisode(start, BackPainSeverity.MODERATE, BackRegion.LOWER, BackSide.RIGHT),
                backPainEpisode(start.plusDays(2), BackPainSeverity.SEVERE, BackRegion.UPPER, BackSide.CENTER),
                backPainEpisode(start.plusDays(3), BackPainSeverity.NONE, null, null)
            ));

        BackPainSummary summary = service(properties(true)).backPainSummary(user, start, end);

        assertEquals(4, summary.checkInCount());
        assertEquals(3, summary.episodeCount());
        assertEquals(2, summary.painDayCount());
        assertEquals(java.util.Map.of(BackPainSeverity.MILD, 1, BackPainSeverity.MODERATE, 1, BackPainSeverity.SEVERE, 1), summary.episodesBySeverity());
        assertEquals(java.util.Map.of(BackRegion.LOWER, 2, BackRegion.UPPER, 1), summary.episodesByRegion());
        assertEquals(java.util.Map.of(BackSide.LEFT, 1, BackSide.RIGHT, 1, BackSide.CENTER, 1), summary.episodesBySide());
    }

    @Test
    void archiveReturnsPagedNewestFirstItemsAndPageMetadata() {
        User user = user();
        LocalDate friday = LocalDate.of(2026, 9, 25);
        SavedWeeklySummary summary = new SavedWeeklySummary();
        summary.setUser(user);
        summary.setFridayDate(friday);
        summary.setCreatedAt(Instant.parse("2026-09-28T06:00:00Z"));
        Pageable expectedPage = PageRequest.of(1, 10, Sort.by(Sort.Order.desc("fridayDate"), Sort.Order.desc("id")));
        when(savedSummaryRepository.findByUserOrderByFridayDateDescIdDesc(eq(user), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(summary), expectedPage, 25));
        when(weeklyReflectionRepository.findByWeeklySummaryIn(List.of(summary))).thenReturn(List.of());

        var response = service(properties(true)).archive(user, 1, 10, null);

        assertEquals(List.of(friday), response.items().stream().map(item -> item.fridayDate()).toList());
        assertEquals(1, response.page());
        assertEquals(10, response.size());
        assertEquals(25, response.totalElements());
        assertEquals(3, response.totalPages());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(savedSummaryRepository).findByUserOrderByFridayDateDescIdDesc(eq(user), pageable.capture());
        assertEquals(expectedPage, pageable.getValue());
    }

    @Test
    void archiveFindsThePageContainingAnOffPageDeepLink() {
        User user = user();
        LocalDate selectedFriday = LocalDate.of(2026, 4, 10);
        when(savedSummaryRepository.existsByUserAndFridayDate(user, selectedFriday)).thenReturn(true);
        when(savedSummaryRepository.countByUserAndFridayDateAfter(user, selectedFriday)).thenReturn(24L);
        Pageable expectedPage = PageRequest.of(2, 10, Sort.by(Sort.Order.desc("fridayDate"), Sort.Order.desc("id")));
        when(savedSummaryRepository.findByUserOrderByFridayDateDescIdDesc(eq(user), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), expectedPage, 25));

        var response = service(properties(true)).archive(user, 0, 10, selectedFriday);

        assertEquals(2, response.page());
        assertEquals(25, response.totalElements());
        verify(savedSummaryRepository).countByUserAndFridayDateAfter(user, selectedFriday);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(savedSummaryRepository).findByUserOrderByFridayDateDescIdDesc(eq(user), pageable.capture());
        assertEquals(expectedPage, pageable.getValue());
    }

    @Test
    void archiveRejectsOutOfBoundsPageAndPageSize() {
        WeeklySummaryService service = service(properties(true));
        User user = user();

        assertThrows(BadRequestException.class, () -> service.archive(user, -1, 10, null));
        assertThrows(BadRequestException.class, () -> service.archive(user, 0, 101, null));
        verifyNoInteractions(savedSummaryRepository);
    }

    @ParameterizedTest
    @CsvSource({"2026-08-14,2026-08-07", "2026-08-15,2026-08-07", "2026-08-16,2026-08-07", "2026-08-17,2026-08-14"})
    void latestPreviewAndCreationSelectTheLatestClosedOutcomeWeek(String todayValue, String fridayValue) {
        User user = user();
        LocalDate today = LocalDate.parse(todayValue);
        LocalDate friday = LocalDate.parse(fridayValue);
        WeeklySummaryService service = service(properties(true));
        when(snapshotService.getReadOnly(eq(user), any(LocalDate.class)))
            .thenAnswer(invocation -> status(invocation.getArgument(1)));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(savedSummaryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var preview = service.preview(user, today);
        assertEquals(friday, preview.fridayDate());
        assertEquals(friday.minusDays(6), preview.periodStart());
        assertEquals(true, preview.canCreate());
        assertEquals(false, preview.alreadySaved());
        var created = service.createLatest(user, today);
        assertEquals(friday, created.fridayDate());
        assertEquals(preview.snapshot(), created.snapshot());
        ArgumentCaptor<SavedWeeklySummary> saved = ArgumentCaptor.forClass(SavedWeeklySummary.class);
        verify(savedSummaryRepository).save(saved.capture());
        assertEquals(friday, saved.getValue().getFridayDate());
        verifyNoInteractions(mailSender);
    }

    @ParameterizedTest
    @CsvSource({"2026-08-14,2026-08-07", "2026-08-15,2026-08-07", "2026-08-16,2026-08-07", "2026-08-17,2026-08-14"})
    void repeatedLatestPreviewAndCreationOpenTheImmutableExistingWeek(String todayValue, String fridayValue) {
        User user = user();
        LocalDate today = LocalDate.parse(todayValue);
        LocalDate friday = LocalDate.parse(fridayValue);
        WeeklySummaryService service = service(properties(true));
        var rows = new HashMap<LocalDate, SavedWeeklySummary>();
        when(snapshotService.getReadOnly(eq(user), any(LocalDate.class)))
            .thenAnswer(invocation -> status(invocation.getArgument(1)));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(savedSummaryRepository.findByUserAndFridayDate(eq(user), any()))
            .thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.getArgument(1))));
        when(savedSummaryRepository.findByUserAndFridayDateForUpdate(eq(user), any()))
            .thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.getArgument(1))));
        when(savedSummaryRepository.save(any())).thenAnswer(invocation -> {
            SavedWeeklySummary saved = invocation.getArgument(0);
            rows.put(saved.getFridayDate(), saved);
            return saved;
        });
        var original = service.createLatest(user, today);
        String originalJson = rows.get(friday).getSnapshotJson();
        clearInvocations(snapshotService);
        when(weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(eq(user), any(), any()))
            .thenReturn(List.of(weight(friday, "65.00")));

        var preview = service.preview(user, today);
        assertEquals(friday, preview.fridayDate());
        assertEquals(false, preview.canCreate());
        assertEquals(true, preview.alreadySaved());
        assertEquals(original.snapshot(), preview.snapshot());
        assertEquals(original, service.createLatest(user, today));
        assertEquals(originalJson, rows.get(friday).getSnapshotJson());
        verify(savedSummaryRepository, times(1)).save(any());
        verifyNoInteractions(snapshotService, mailSender);
    }

    @ParameterizedTest
    @CsvSource({"2026-08-14", "2026-08-15", "2026-08-16"})
    void explicitCreationStillRejectsTheUnfinishedFridayOutcomeWindow(String todayValue) {
        WeeklySummaryService service = service(properties(true));

        assertThrows(BadRequestException.class,
            () -> service.createForFriday(user(), LocalDate.of(2026, 8, 14), LocalDate.parse(todayValue)));

        verifyNoInteractions(userRepository, savedSummaryRepository, snapshotService, mailSender);
    }

    @ParameterizedTest
    @CsvSource({"2026-08-14,2026-08-07", "2026-08-15,2026-08-07", "2026-08-16,2026-08-07", "2026-08-17,2026-08-14"})
    void manualEmailSendsOnlyTheLatestClosedOutcomeWeek(String todayValue, String fridayValue) throws Exception {
        User user = user();
        LocalDate today = LocalDate.parse(todayValue);
        LocalDate friday = LocalDate.parse(fridayValue);
        WeeklySummarySnapshot snapshot = new WeeklySummarySnapshot(1, friday.minusDays(6), friday,
            null, new WeeklySummarySnapshot.OutcomeMeasurements(null, null), List.of(), null, List.of(), List.of("Original evidence"));
        SavedWeeklySummary saved = new SavedWeeklySummary();
        saved.setUser(user);
        saved.setFridayDate(friday);
        saved.setSnapshotJson(new ObjectMapper().findAndRegisterModules().writeValueAsString(snapshot));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(savedSummaryRepository.findByUserAndFridayDateForUpdate(user, friday)).thenReturn(Optional.of(saved));
        WeeklySummaryService service = service(properties(true));

        try (var dates = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            dates.when(() -> LocalDate.now(DateTimes.USER_ZONE)).thenReturn(today);
            service.send(user);
        }

        verify(mailSender).send(user, snapshot, null);
        verify(savedSummaryRepository).findByUserAndFridayDateForUpdate(user, friday);
        verifyNoInteractions(snapshotService);
    }

    @Test
    void autumnClockChangeKeepsSundayOutcomesInTheCorrectMadridWeek() {
        WeeklySummaryService service = service(properties(true));
        User user = user();
        LocalDate friday = LocalDate.of(2026, 10, 23);
        LocalDate sunday = friday.plusDays(2);
        when(weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(eq(user), any(), any()))
            .thenReturn(List.of(weightAt(sunday, 3, "69.1")));
        when(bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(eq(user), any(), any()))
            .thenReturn(List.of(bloodPressureAt(sunday, 3, 118, 78)));

        WeeklySummarySnapshot.OutcomeMeasurements outcomes = service.outcomeMeasurements(user, friday);

        assertEquals(LocalDate.of(2026, 10, 16), service.latestClosedOutcomeWeekEnd(LocalDate.of(2026, 10, 25)));
        assertEquals(friday, service.latestClosedOutcomeWeekEnd(LocalDate.of(2026, 10, 26)));
        assertEquals(sunday, outcomes.weight().measuredDate());
        assertEquals(sunday, outcomes.bloodPressure().measuredDate());
    }

    @Test
    void weekendOutcomesChooseLatestFridayWeightAndSaturdayBloodPressureIndependently() {
        User user = user();
        LocalDate friday = LocalDate.of(2026, 8, 14);
        Weight earlyFriday = weightAt(friday, 8, "70.0");
        Weight latestFriday = weightAt(friday, 20, "69.7");
        BloodPressure earlySaturday = bloodPressureAt(friday.plusDays(1), 8, 122, 82);
        BloodPressure latestSaturday = bloodPressureAt(friday.plusDays(1), 22, 120, 80);
        when(weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(eq(user), any(), any()))
            .thenReturn(List.of(earlyFriday, latestFriday, weightAt(friday.plusDays(1), 8, "69.5")));
        when(bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(eq(user), any(), any()))
            .thenReturn(List.of(earlySaturday, latestSaturday, bloodPressureAt(friday.plusDays(2), 8, 119, 79)));

        WeeklySummarySnapshot.OutcomeMeasurements outcomes = service(properties(true)).outcomeMeasurements(user, friday);

        assertEquals(friday, outcomes.weight().measuredDate());
        assertEquals(new BigDecimal("69.7"), outcomes.weight().weightKg());
        assertEquals(friday.plusDays(1), outcomes.bloodPressure().measuredDate());
        assertEquals(120, outcomes.bloodPressure().systolic());
        assertEquals(80, outcomes.bloodPressure().diastolic());
    }

    @Test
    void routineProgressCountsDistinctEligibleLocalDaysAndRetainsExactlySixtyPercent() {
        User user = user();
        LocalDate start = LocalDate.of(2026, 8, 8);
        Routine atSixtyPercent = routine(user, start.plusDays(2), "60% routine");
        Routine belowSixtyPercent = routine(user, start.plusDays(2), "Below 60% routine");
        Routine startsAfterWeek = routine(user, start.plusDays(7), "No opportunities");
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of(atSixtyPercent, belowSixtyPercent, startsAfterWeek));
        when(routineCheckinRepository.findByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(eq(atSixtyPercent), any(), any()))
            .thenReturn(List.of(checkin(atSixtyPercent, start.plusDays(2), 8), checkin(atSixtyPercent, start.plusDays(2), 20), checkin(atSixtyPercent, start.plusDays(3), 8), checkin(atSixtyPercent, start.plusDays(4), 8)));
        when(routineCheckinRepository.findByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(eq(belowSixtyPercent), any(), any()))
            .thenReturn(List.of(checkin(belowSixtyPercent, start.plusDays(2), 8), checkin(belowSixtyPercent, start.plusDays(2), 20), checkin(belowSixtyPercent, start.plusDays(3), 8)));

        List<WeeklySummarySnapshot.RoutineProgress> routines = service(properties(true)).routineProgress(user, start, start.plusDays(6));

        assertEquals(2, routines.size());
        assertEquals(5, routines.get(0).eligibleDays());
        assertEquals(3, routines.get(0).completedDays());
        assertEquals(new BigDecimal("60.00"), routines.get(0).percentage());
        assertEquals(2, routines.get(1).completedDays());
        assertEquals(new BigDecimal("40.00"), routines.get(1).percentage());
    }

    @Test
    void repeatedSummaryCreationReturnsTheExistingImmutableSnapshot() {
        User user = user();
        LocalDate friday = LocalDate.of(2026, 8, 14);
        SavedWeeklySummary existing = new SavedWeeklySummary();
        existing.setUser(user);
        existing.setFridayDate(friday);
        existing.setSnapshotJson("{\"original\":true}");
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(savedSummaryRepository.findByUserAndFridayDateForUpdate(user, friday)).thenReturn(Optional.of(existing));

        WeeklySummaryService.CreationResult result = service(properties(true)).createForFridayIfMissing(user, friday, LocalDate.of(2026, 8, 17));

        assertEquals(existing, result.summary());
        assertEquals(false, result.created());
        assertEquals("{\"original\":true}", result.summary().getSnapshotJson());
        verifyNoInteractions(mailSender);
    }

    @Test
    void progressBuildsCurrentPreviousAndYearAgoSaturdayToFridayWeeks() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        when(snapshotService.getReadOnly(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(LocalDate.class)))
            .thenAnswer(invocation -> status(invocation.getArgument(1)));
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of());
        when(weightRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(weight(end, "68.50")));
        when(bloodPressureRepository.findByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(bloodPressure(end, 120, 80)));
        when(moodRepository.findByUserAndMoodDateBetweenOrderByMoodDateAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(sleepRepository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        Sickness cold = new Sickness();
        cold.setUser(user);
        cold.setSicknessDate(end.minusDays(1));
        cold.setType(SicknessType.COLD);
        cold.setSeverity(SicknessSeverity.LOW);
        when(sicknessRepository.findByUserAndSicknessDateBetweenOrderBySicknessDateAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(cold));
        when(calorieService.findBetween(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(decisionOutcomeRepository.findByUserAndOutcomeDateBetweenOrderByOutcomeDateAscIdAsc(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        WeeklyMetrics.Progress progress = service(properties(true)).buildProgress(user, end);

        assertEquals(LocalDate.of(2026, 8, 8), progress.currentPeriod().startDate());
        assertEquals(LocalDate.of(2026, 8, 1), progress.previousComparablePeriod().startDate());
        assertEquals(LocalDate.of(2025, 8, 9), progress.yearAgoComparablePeriod().startDate());
        assertEquals(7, progress.currentPeriod().routineCompletion().days().size());
        assertEquals(new BigDecimal("68.50"), progress.currentPeriod().weight().weightKg());
        assertEquals(new BigDecimal("120.00"), progress.currentPeriod().bloodPressure().systolic());
        assertEquals(1L, progress.currentPeriod().sicknessesByType().get("COLD"));
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

    @Test
    void schedulerDoesNotSendDuringOptInBackfillMode() {
        AppProperties enabledProperties = properties(true);
        WeeklySummaryService service = service(enabledProperties);
        WeeklySummaryScheduler scheduler = new WeeklySummaryScheduler(userRepository, service, enabledProperties);
        org.springframework.test.util.ReflectionTestUtils.setField(scheduler, "backfillMode", "dry-run");

        scheduler.sendScheduledSummary();

        verifyNoInteractions(userRepository, mailSender);
    }

    @Test
    void manualEmailSendRejectsOtherAccountsBeforeCheckingConfiguration() {
        User other = user();
        other.setEmail("other@example.com");
        WeeklySummaryService service = service(properties(false));

        var exception = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.send(other));

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(userRepository, savedSummaryRepository, mailSender);
    }

    private WeeklySummaryService service(AppProperties properties) {
        return new WeeklySummaryService(
            weightRepository,
            bloodPressureRepository,
            moodRepository,
            sleepRepository,
            sicknessRepository,
            backPainEpisodeRepository,
            calorieService,
            workoutRepository,
            decisionOutcomeRepository,
            routineRepository,
            routineCheckinRepository,
            snapshotService,
            personalRecordService,
            new WeeklyMetricsCalculator(),
            mailSender,
            userRepository,
            savedSummaryRepository,
            weeklyReflectionRepository,
            coachingPlanRepository,
            new ObjectMapper().findAndRegisterModules(),
            properties
        );
    }

    private BackPainEpisode backPainEpisode(LocalDate date, BackPainSeverity severity, BackRegion region, BackSide side) {
        BackPainEpisode episode = new BackPainEpisode();
        episode.setEpisodeDate(date);
        episode.setSeverity(severity);
        episode.setRegion(region);
        episode.setSide(side);
        return episode;
    }

    private List<DailyStatus> statuses(LocalDate start) {
        return start.datesUntil(start.plusDays(7)).map(this::status).toList();
    }

    private WeeklyMetricsCalculator.Input emptyInput() {
        return new WeeklyMetricsCalculator.Input(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private Weight weight(LocalDate date, String value) {
        return weightAt(date, 8, value);
    }

    private Weight weightAt(LocalDate date, int hour, String value) {
        Weight weight = new Weight();
        weight.setMeasuredAt(DateTimes.startOfDay(date).plusHours(hour));
        weight.setWeight(new BigDecimal(value));
        return weight;
    }

    private BloodPressure bloodPressure(LocalDate date, int upper, int lower) {
        return bloodPressureAt(date, 9, upper, lower);
    }

    private BloodPressure bloodPressureAt(LocalDate date, int hour, int upper, int lower) {
        BloodPressure bloodPressure = new BloodPressure();
        bloodPressure.setMeasuredAt(DateTimes.startOfDay(date).plusHours(hour));
        bloodPressure.setUpper(upper);
        bloodPressure.setLower(lower);
        return bloodPressure;
    }

    private Routine routine(User user, LocalDate startDate, String name) {
        Routine routine = new Routine();
        routine.setUser(user);
        routine.setStartDate(DateTimes.startOfDay(startDate));
        routine.setName(name);
        return routine;
    }

    private RoutineCheckin checkin(Routine routine, LocalDate date, int hour) {
        RoutineCheckin checkin = new RoutineCheckin();
        checkin.setRoutine(routine);
        checkin.setCheckedAt(DateTimes.startOfDay(date).plusHours(hour));
        return checkin;
    }

    private DailyStatus status(LocalDate date) {
        DailyStatus status = new DailyStatus();
        status.setStatusDate(date);
        status.setRoutinesDone(1);
        status.setTotalRoutines(2);
        status.setTotalWeightRoutines(0);
        status.setTotalBloodPressureRoutines(0);
        status.setTotalFlexibilityRoutines(0);
        status.setTotalMindRoutines(0);
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
