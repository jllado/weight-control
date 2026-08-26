package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.PersonalRecordSnapshotRepository;
import com.jllado.weightcontrol.repository.PersonalRecordSettingRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalRecordServiceTest {

    @Mock
    private PersonalRecordSnapshotRepository repository;
    @Mock
    private PersonalRecordSettingRepository settingRepository;
    @Mock
    private WeightService weightService;
    @Mock
    private WorkoutService workoutService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoutineService routineService;

    private PersonalRecordService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PersonalRecordService(repository, settingRepository, new PersonalRecordCalculator(), weightService, workoutService,
            mock(BloodPressureService.class), mock(LipidPanelService.class), mock(MoodService.class), mock(SleepService.class), mock(MealService.class),
            mock(HabitService.class), routineService, mock(DailyStatusRepository.class), userRepository);
        user = new User();
        user.setId(1L);
        lenient().when(settingRepository.findByUser(user)).thenReturn(List.of());
        lenient().when(userRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(user));
    }

    @Test
    void currentReadsAndFiltersTheOwnedSnapshot() {
        Exercise squat = new Exercise();
        squat.setId(10L);
        squat.setName("Squat");
        PersonalRecordSnapshot body = snapshot(PersonalRecordMetric.BODY_WEIGHT, null, null, "79");
        PersonalRecordSnapshot workout = snapshot(PersonalRecordMetric.WORKOUT_REPETITIONS, squat, "40", "12");
        when(repository.findByUser(user)).thenReturn(List.of(workout, body));

        var records = service.current(user, PersonalRecordDomain.WORKOUT, null, 10L);

        assertEquals(1, records.size());
        assertEquals("Squat", records.getFirst().subject().label());
        assertEquals(new BigDecimal("40"), records.getFirst().qualifier().loadKg());
        verify(repository).findByUser(user);
    }

    @Test
    void historyIsSourceDerivedFilteredAndPaginated() {
        when(weightService.findAll(user)).thenReturn(List.of(
            weight(1L, "2026-08-01T08:00:00+02:00", "80"),
            weight(2L, "2026-08-08T08:00:00+02:00", "79"),
            weight(3L, "2026-08-15T08:00:00+02:00", "79")
        ));
        when(workoutService.findAll(user)).thenReturn(List.of());

        var page = service.history(user, PersonalRecordDomain.BODY, PersonalRecordMetric.BODY_WEIGHT, null, Set.of(), null, 0, 2);

        assertEquals(3, page.totalElements());
        assertEquals(2, page.items().size());
        assertEquals(PersonalRecordEventKind.TIED, page.items().getFirst().kind());
        var exact = service.history(user, null, null, null, Set.of(), page.items().getFirst().eventKey(), 0, 25);
        assertEquals(1, exact.totalElements());
        assertEquals(page.items().getFirst().eventKey(), exact.items().getFirst().eventKey());
    }

    @Test
    void workoutHistoryCalculatesOnlyTheRequestedWorkoutEvents() {
        Exercise exercise = new Exercise();
        exercise.setId(3L);
        exercise.setName("Squat");
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        Workout first = workout(10L, LocalDate.of(2026, 8, 1), exercise, 5);
        Workout selected = workout(11L, LocalDate.of(2026, 8, 8), exercise, 8);
        when(workoutService.findAll(user)).thenReturn(List.of(first, selected));

        var events = service.workoutHistory(user, Set.of(11L));

        var repetitionEvent = events.stream().filter(event -> event.metric() == PersonalRecordMetric.WORKOUT_REPETITIONS).findFirst().orElseThrow();
        assertEquals(PersonalRecordEventKind.IMPROVED, repetitionEvent.kind());
        assertEquals(11L, repetitionEvent.source().id());
        verify(workoutService).findAll(user);
        verifyNoInteractions(weightService, routineService);
    }

    @Test
    void routineAchievementsOccurOnlyWhenANewBestReachesAMilestone() {
        Routine routine = new Routine();
        routine.setId(7L);
        routine.setName("Walk");
        routine.setBestStrike(21);
        RoutineCheckin checkin = new RoutineCheckin();
        checkin.setId(8L);
        checkin.setRoutine(routine);
        checkin.setCheckedAt(OffsetDateTime.parse("2026-08-20T08:00:00+02:00"));

        var milestone = service.routineMilestoneAchievement(user, new RoutineService.RoutineCheckinResult(routine, checkin, 20));
        routine.setBestStrike(22);
        var ordinaryDay = service.routineMilestoneAchievement(user, new RoutineService.RoutineCheckinResult(routine, checkin, 21));

        assertEquals(1, milestone.size());
        assertEquals(new BigDecimal("21"), milestone.getFirst().value());
        assertEquals(0, ordinaryDay.size());
    }

    @Test
    void coachContextReturnsAllCurrentRecordsAndOnlyRangedProgressionWithoutSourceIdentifiers() {
        when(weightService.findAll(user)).thenReturn(List.of(
            weight(1L, "2026-08-01T08:00:00+02:00", "80"),
            weight(2L, "2026-08-08T08:00:00+02:00", "79"),
            weight(3L, "2026-08-15T08:00:00+02:00", "78")
        ));
        when(workoutService.findAll(user)).thenReturn(List.of());

        var context = service.coachContext(
            user,
            java.time.LocalDate.parse("2026-08-08"),
            java.time.LocalDate.parse("2026-08-10"),
            0,
            10
        );

        assertEquals(10, context.current().size());
        assertEquals(10, context.progression().size());
        assertEquals(15, context.currentTotal());
        assertEquals(15, context.progressionTotal());
        assertTrue(context.hasMore());
        assertEquals(java.time.LocalDate.parse("2026-08-08"), context.progression().getFirst().recordDate());
        assertEquals("Body", context.progression().getFirst().subjectLabel());

        var lastPage = service.coachContext(
            user,
            java.time.LocalDate.parse("2026-08-08"),
            java.time.LocalDate.parse("2026-08-10"),
            1,
            10
        );

        assertEquals(5, lastPage.current().size());
        assertEquals(5, lastPage.progression().size());
        assertFalse(lastPage.hasMore());
    }

    @Test
    void rebuildReturnsOnlyStrictCreateAchievements() {
        Weight weight = weight(2L, "2026-08-08T08:00:00+02:00", "79");
        when(weightService.findAll(user)).thenReturn(List.of(weight));
        when(workoutService.findAll(user)).thenReturn(List.of());
        Map<String, BigDecimal> previous = Map.of(
            "BODY_WEIGHT", new BigDecimal("80"),
            "BODY_FAT_MASS", new BigDecimal("17"),
            "BODY_FAT_PERCENTAGE", new BigDecimal("21"),
            "BODY_MUSCLE_MASS", new BigDecimal("63"),
            "BODY_MUSCLE_PERCENTAGE", new BigDecimal("79")
        );

        var achievements = service.rebuildAndFindAchievements(user, previous, PersonalRecordSourceType.WEIGHT, 2L, true);

        assertEquals(5, achievements.size());
        assertEquals(PersonalRecordEventKind.IMPROVED, achievements.getFirst().kind());
        verify(repository).deleteByUser(user);
        verify(repository).saveAll(anyList());
        verify(userRepository).findByIdForUpdate(1L);

        assertEquals(List.of(), service.rebuildAndFindAchievements(user, previous, PersonalRecordSourceType.WEIGHT, 2L, false));
    }

    @Test
    void catalogUsesDefaultsAndOwnedOverrides() {
        PersonalRecordSetting setting = new PersonalRecordSetting();
        setting.setMetric(PersonalRecordCatalogMetric.BODY_WEIGHT);
        setting.setMode(PersonalRecordMode.BOTH);
        when(settingRepository.findByUser(user)).thenReturn(List.of(setting));

        var catalog = service.catalog(user);

        assertEquals(PersonalRecordCatalogMetric.values().length, catalog.size());
        assertEquals(PersonalRecordMode.BOTH, catalog.stream().filter(metric -> metric.key() == PersonalRecordCatalogMetric.BODY_WEIGHT).findFirst().orElseThrow().mode());
        assertEquals(PersonalRecordMode.MAXIMUM, catalog.stream().filter(metric -> metric.key() == PersonalRecordCatalogMetric.MOOD).findFirst().orElseThrow().defaultMode());
        assertTrue(catalog.stream().filter(metric -> metric.domain() == PersonalRecordDomain.NUTRITION)
            .allMatch(metric -> metric.defaultMode() == PersonalRecordMode.DISABLED && metric.mode() == PersonalRecordMode.DISABLED));
    }

    @Test
    void replacingSettingsStoresOnlyOverridesAndRebuildsWithoutAchievements() {
        service.replaceSettings(user, new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingsRequest(List.of(
            new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingOverrideRequest(PersonalRecordCatalogMetric.BODY_WEIGHT, PersonalRecordMode.BOTH),
            new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingOverrideRequest(PersonalRecordCatalogMetric.MOOD, PersonalRecordMode.MAXIMUM)
        )));

        verify(settingRepository).deleteByUser(user);
        verify(settingRepository).saveAll(argThat(settings -> {
            var iterator = settings.iterator();
            return iterator.hasNext() && iterator.next().getMetric() == PersonalRecordCatalogMetric.BODY_WEIGHT && !iterator.hasNext();
        }));
        verify(repository).deleteByUser(user);
    }

    @Test
    void replacingSettingsRejectsDuplicateMetricsBeforeChangingOwnedSettings() {
        var request = new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingsRequest(List.of(
            new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingOverrideRequest(PersonalRecordCatalogMetric.BODY_WEIGHT, PersonalRecordMode.MINIMUM),
            new com.jllado.weightcontrol.api.dto.PersonalRecordDtos.SettingOverrideRequest(PersonalRecordCatalogMetric.BODY_WEIGHT, PersonalRecordMode.MAXIMUM)
        ));

        assertThrows(BadRequestException.class, () -> service.replaceSettings(user, request));
        verify(settingRepository, never()).deleteByUser(any());
    }

    private PersonalRecordSnapshot snapshot(PersonalRecordMetric metric, Exercise exercise, String load, String value) {
        PersonalRecordSnapshot snapshot = new PersonalRecordSnapshot();
        snapshot.setUser(user);
        snapshot.setSeriesKey(metric.name());
        snapshot.setDomain(metric.getDomain());
        snapshot.setMetric(metric);
        snapshot.setDirection(metric.getDirection());
        snapshot.setExercise(exercise);
        snapshot.setLoadKg(load == null ? null : new BigDecimal(load));
        snapshot.setValue(new BigDecimal(value));
        snapshot.setRecordDate(java.time.LocalDate.of(2026, 8, 8));
        snapshot.setSourceType(exercise == null ? PersonalRecordSourceType.WEIGHT : PersonalRecordSourceType.WORKOUT);
        snapshot.setSourceId(2L);
        return snapshot;
    }

    private Weight weight(Long id, String date, String value) {
        Weight weight = new Weight();
        weight.setId(id);
        weight.setMeasuredAt(OffsetDateTime.parse(date));
        weight.setWeight(new BigDecimal(value));
        weight.setFat(new BigDecimal(value).subtract(new BigDecimal("64")));
        weight.setFatPercentage(new BigDecimal(value).subtract(new BigDecimal("60")));
        weight.setMuscle(new BigDecimal("65"));
        weight.setMusclePercentage(new BigDecimal("82"));
        return weight;
    }

    private Workout workout(Long id, LocalDate date, Exercise exercise, int repetitions) {
        Workout workout = new Workout();
        workout.setId(id);
        workout.setWorkoutDate(date);
        WorkoutLine line = new WorkoutLine();
        line.setWorkout(workout);
        line.setExercise(exercise);
        line.setPosition(0);
        WorkoutSegment segment = new WorkoutSegment();
        segment.setWorkoutLine(line);
        segment.setPosition(0);
        segment.setRepetitions(repetitions);
        line.getSegments().add(segment);
        workout.getLines().add(line);
        return workout;
    }
}
