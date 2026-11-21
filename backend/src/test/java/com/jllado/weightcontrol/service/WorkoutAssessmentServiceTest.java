package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.domain.WorkoutPlanTargetSnapshot;
import com.jllado.weightcontrol.domain.WorkoutPlanSessionEvidence;
import com.jllado.weightcontrol.domain.WorkoutPlanDay;
import com.jllado.weightcontrol.domain.WorkoutPlan;
import com.jllado.weightcontrol.domain.StretchingUnit;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentWorkoutData;
import com.jllado.weightcontrol.repository.WorkoutPlanRepository;
import java.math.BigDecimal;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.SaveWorkoutAssessmentRequest;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutAssessment;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.repository.CoachingPlanRepository;
import com.jllado.weightcontrol.repository.HealthConstraintRepository;
import com.jllado.weightcontrol.repository.WorkoutAssessmentRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkoutAssessmentServiceTest {

    private static final LocalDate WORKOUT_DATE = LocalDate.of(2026, 8, 20);
    private static final Instant WORKOUT_UPDATED_AT = Instant.parse("2026-08-20T18:00:00Z");
    private static final Instant PLAN_UPDATED_AT = Instant.parse("2026-08-20T17:00:00Z");

    @Mock
    private WorkoutRepository workoutRepository;
    @Mock
    private WorkoutPlanRepository workoutPlanRepository;
    @Mock
    private WorkoutAssessmentRepository assessmentRepository;
    @Mock
    private CoachingPlanRepository coachingPlanRepository;
    @Mock
    private HealthConstraintRepository healthConstraintRepository;

    @Mock
    private com.jllado.weightcontrol.repository.UserRepository userRepository;

    @InjectMocks
    private WorkoutAssessmentService service;

    private User user;
    private Workout workout;
    private CoachingPlan plan;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        org.mockito.Mockito.lenient().when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        workout = workout(WORKOUT_DATE, exercise(10L, "Bench press"));
        workout.setUpdatedAt(WORKOUT_UPDATED_AT);
        plan = new CoachingPlan();
        plan.setUser(user);
        plan.setGoal("Improve upper-body strength");
        plan.setStartDate(WORKOUT_DATE.minusDays(30));
        plan.setUpdatedAt(PLAN_UPDATED_AT);
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void stretchingRemainsInAssessmentDetailsButDoesNotSelectComparableWorkouts(boolean breaths) {
        Exercise stretch = exercise(40L, "Calf stretch");
        stretch.setExerciseType(ExerciseType.STRETCHING);
        stretch.setTrackingMode(ExerciseTrackingMode.SECONDS);
        workout = workout(WORKOUT_DATE, exercise(10L, "Bench press"), stretch);
        var segment = workout.getLines().get(1).getSegments().getFirst();
        segment.setRepetitions(null);
        segment.setDurationSeconds(breaths ? null : 30);
        segment.setBreaths(breaths ? 5 : null);
        workout.getLines().get(1).setStretchingUnit(breaths ? StretchingUnit.BREATHS : StretchingUnit.SECONDS);
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(workout));
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.of(plan));
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, WORKOUT_DATE.minusDays(90), WORKOUT_DATE.minusDays(1)))
            .thenReturn(List.of(workout(WORKOUT_DATE.minusDays(1), stretch), workout(WORKOUT_DATE.minusDays(2), exercise(10L, "Bench press"), stretch)));
        var context = service.getContext(user, WORKOUT_DATE, null);
        assertEquals(ExerciseType.STRETCHING, context.workout().sessions().getFirst().lines().get(1).exerciseType());
        assertEquals(breaths ? null : 30, context.workout().sessions().getFirst().lines().get(1).segments().getFirst().durationSeconds());
        assertEquals(breaths ? 5 : null, context.workout().sessions().getFirst().lines().get(1).segments().getFirst().breaths());
        assertEquals(1, context.recentComparableTraining().size());
        assertEquals(List.of("Bench press"), context.recentComparableTraining().getFirst().sessions().getFirst().lines().stream().map(line -> line.exercise()).toList());
        workout = workout(WORKOUT_DATE, stretch);
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(workout));
        assertEquals(List.of(), service.getContext(user, WORKOUT_DATE, null).recentComparableTraining());
    }

    @Test
    void contextReturnsOnlyMatchingPriorTrainingNewestFirstWithoutIdentifiers() throws Exception {
        Workout newestMatch = workout(WORKOUT_DATE.minusDays(4), exercise(10L, "Bench press"), exercise(30L, "Squat"));
        Workout olderMatch = workout(WORKOUT_DATE.minusDays(40), exercise(10L, "Bench press"));
        Workout nonMatch = workout(WORKOUT_DATE.minusDays(2), exercise(20L, "Running"));
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(workout));
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.of(plan));
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(
            user,
            WORKOUT_DATE.minusDays(90),
            WORKOUT_DATE.minusDays(1)
        )).thenReturn(List.of(olderMatch, nonMatch, newestMatch));
        when(healthConstraintRepository.findActiveOverlapping(any(), any(), any())).thenReturn(List.of());

        var context = service.getContext(user, WORKOUT_DATE, null);

        assertEquals(WORKOUT_DATE, context.workout().date());
        assertEquals(List.of(WORKOUT_DATE.minusDays(4), WORKOUT_DATE.minusDays(40)),
            context.recentComparableTraining().stream().map(item -> item.date()).toList());
        var comparison = context.recentComparableTraining().getFirst().sessions().getFirst();
        assertEquals(List.of("Bench press"), comparison.lines().stream().map(item -> item.exercise()).toList());
        assertNull(comparison.note());
        assertNull(comparison.plannedTargets());
        assertNull(comparison.lines().getFirst().description());
        assertEquals("Bench press description", context.workout().sessions().getFirst().lines().getFirst().description());
        String comparisonJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(comparison);
        assertFalse(comparisonJson.contains("note"));
        assertFalse(comparisonJson.contains("description"));
        assertFalse(comparisonJson.contains("plannedTargets"));
        assertEquals("Improve upper-body strength", context.activePlan().goal());
        assertEquals(WorkoutAssessmentService.contextToken(List.of(workout)), context.workoutContextToken());
        assertEquals(PLAN_UPDATED_AT, context.planUpdatedAt());
    }

    @Test
    void legacyCoachRequestCannotCreateARatingForAnUntouchedWorkout() {
        givenCurrentContext();
        assertThrows(NotFoundException.class, () -> service.save(user, WORKOUT_DATE, null, request(true, 8, "Legacy estimate.")));
        verify(assessmentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void legacyCoachRequestReturnsTheSavedAssessmentWithoutOverwritingIt() {
        var existing = assessment(workout, 5);
        when(assessmentRepository.findByUserAndWorkoutDate(user, WORKOUT_DATE)).thenReturn(Optional.of(existing));
        givenCurrentContext();
        var result = service.save(user, WORKOUT_DATE, null, request(true, 9, "Proposed different rating."));
        assertEquals(5, result.goalAlignmentScore());
        assertEquals("Clear alignment with the active goal.", result.rationale());
        verify(assessmentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void saveRejectsMissingConfirmationInvalidScoresAndLongText() {
        assertThrows(BadRequestException.class,
            () -> service.save(user, WORKOUT_DATE, null, request(false, 8, "Valid rationale.")));
        assertThrows(BadRequestException.class,
            () -> service.save(user, WORKOUT_DATE, null, request(true, 11, "Valid rationale.")));
        String longRationale = String.join(" ", java.util.Collections.nCopies(26, "word"));
        assertThrows(BadRequestException.class,
            () -> service.save(user, WORKOUT_DATE, null, request(true, 8, longRationale)));
    }

    @Test
    void saveRejectsStaleWorkoutOrPlanContext() {
        givenCurrentContext();

        assertThrows(BadRequestException.class, () -> service.save(
            user,
            WORKOUT_DATE, null,
            new SaveWorkoutAssessmentRequest(
                8,
                7,
                "Valid rationale.",
                "Clear strength.",
                "Small improvement.",
                "Repeat next week.",
                PLAN_UPDATED_AT.plusSeconds(1),
                WorkoutAssessmentService.contextToken(List.of(workout)),
                true
            )
        ));
    }

    @Test
    void contextRequiresAnOwnedWorkoutAndWorksWithoutACoachingPlan() {
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of());
        assertThrows(NotFoundException.class, () -> service.getContext(user, WORKOUT_DATE, null));

        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(workout));
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.empty());
        assertNull(service.getContext(user, WORKOUT_DATE, null).activePlan());
    }

    @Test
    void multipleSessionsFormOneDayAndSessionTargetingIsRejected() {
        Workout later = workout(WORKOUT_DATE, exercise(11L, "Bike"));
        givenCurrentContext();
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(workout, later));
        var context = service.getContext(user, WORKOUT_DATE, null);
        assertEquals(2, context.workout().sessions().size());
        assertEquals(List.of("Bench press", "Bike"), context.workout().sessions().stream().flatMap(session -> session.lines().stream()).map(line -> line.exercise()).toList());
        assertThrows(BadRequestException.class, () -> service.getContext(user, WORKOUT_DATE, workout.getSessionReference()));
        assertThrows(BadRequestException.class, () -> service.save(user, WORKOUT_DATE, workout.getSessionReference(), request(true, 8, "Valid rationale.")));
    }

    @Test
    void addingDeletingOrEditingAnySessionRejectsThePreviousContext() {
        givenCurrentContext();
        Workout later = workout(WORKOUT_DATE, exercise(11L, "Bike"));
        var original = request(true, 8, "Valid rationale.");
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(workout, later));
        assertThrows(BadRequestException.class, () -> service.save(user, WORKOUT_DATE, null, original));
        var combined = new SaveWorkoutAssessmentRequest(8, 7, "Valid rationale.", "Strength.", "Improvement.", "Next action.", PLAN_UPDATED_AT, WorkoutAssessmentService.contextToken(List.of(workout, later)), true);
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(later));
        assertThrows(BadRequestException.class, () -> service.save(user, WORKOUT_DATE, null, combined));
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(workout));
        workout.setUpdatedAt(WORKOUT_UPDATED_AT.plusSeconds(1));
        assertThrows(BadRequestException.class, () -> service.save(user, WORKOUT_DATE, null, original));
    }

    @Test
    void comparableLimitAppliesToDaysRatherThanSessions() {
        givenCurrentContext();
        var history = java.util.stream.IntStream.rangeClosed(1, 12).boxed().flatMap(day -> java.util.stream.IntStream.range(0, 2).mapToObj(session -> workout(WORKOUT_DATE.minusDays(day), exercise(10L, "Bench press")))).toList();
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, WORKOUT_DATE.minusDays(90), WORKOUT_DATE.minusDays(1))).thenReturn(history);
        var context = service.getContext(user, WORKOUT_DATE, null);
        assertEquals(3, context.recentComparableTraining().size());
        assertEquals(WORKOUT_DATE.minusDays(3), context.recentComparableTraining().getLast().date());
        org.junit.jupiter.api.Assertions.assertTrue(context.recentComparableTraining().stream().allMatch(day -> day.sessions().size() == 2));
    }

    @Test
    void contextExplicitlyReportsUnavailableWeeklyPlanRatherThanInferringFailure() {
        givenCurrentContext();
        var tree = new ObjectMapper().findAndRegisterModules().valueToTree(service.getContext(user, WORKOUT_DATE, null));
        assertEquals("UNAVAILABLE", tree.path("weeklyPlan").path("evidenceStatus").asText());
        assertTrue(tree.path("weeklyPlan").path("day").isNull());
    }

    @Test
    void canonicalSessionEvidenceKeepsDuplicateTargetsAndIgnoresDecimalScaleAndDescriptions() {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var target = new WorkoutPlanTargetSnapshot("Press", "Original", ExerciseTrackingMode.REPS, ExerciseType.TRAINING, null,
            StretchingUnit.SECONDS,
            List.of(new WorkoutPlanDay.Segment(8, null, new BigDecimal("20.00"), null, null, null, null, null, null)));
        workout.setPlannedTargets(List.of(target, target));
        String repeated = mapper.valueToTree(AssessmentWorkoutData.from(workout)).path("plannedSessionKey").asText();
        assertFalse(repeated.isBlank());
        var equivalent = new WorkoutPlanTargetSnapshot("Press", "Changed description", ExerciseTrackingMode.REPS, ExerciseType.TRAINING, null,
            StretchingUnit.SECONDS,
            List.of(new WorkoutPlanDay.Segment(8, null, new BigDecimal("20.0"), null, null, null, null, null, null)));
        workout.setPlannedTargets(List.of(equivalent, equivalent));
        assertEquals(repeated, mapper.valueToTree(AssessmentWorkoutData.from(workout)).path("plannedSessionKey").asText());
        workout.setPlannedTargets(List.of(target));
        assertFalse(repeated.equals(mapper.valueToTree(AssessmentWorkoutData.from(workout)).path("plannedSessionKey").asText()));
        workout.setPlannedTargets(null);
        assertTrue(mapper.valueToTree(AssessmentWorkoutData.from(workout)).path("plannedSessionKey").isMissingNode());
    }

    @Test
    void contextRetainsRepeatedUnnamedAndSaunaSessionsAndIndependentRecordingTargets() {
        givenCurrentContext();
        var target = new WorkoutPlanDay.Target(10L, "Breathing hold", "Hold", ExerciseTrackingMode.SECONDS,
            ExerciseType.STRETCHING, null, List.of(new WorkoutPlanDay.Segment(null, null, null, null, null, null, null, null, 6)),
            StretchingUnit.BREATHS, null);
        var session = new WorkoutPlanDay.Session(null, "Recovery", List.of(target), true, List.of(12, 8));
        var sauna = new WorkoutPlanDay.Session("Sauna", null, List.of(), true, List.of(10));
        var weekly = weeklyPlan(List.of(session, session, sauna));
        when(workoutPlanRepository.findAssessmentCandidates(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq(WORKOUT_DATE), any(), any())).thenReturn(List.of(weekly));
        var original = WorkoutPlanSessionEvidence.snapshot(target);
        workout.setPlannedTargets(List.of(original));
        workout.setPlannedSaunaRoundsMinutes(List.of(12, 8));
        var context = service.getContext(user, WORKOUT_DATE, null);
        var expected = context.weeklyPlan().day().sessions();
        assertEquals(3, expected.size());
        assertEquals(expected.getFirst().sessionKey(), expected.get(1).sessionKey());
        assertEquals(expected.getFirst().sessionKey(), context.workout().sessions().getFirst().plannedSessionKey());
        assertEquals(6, expected.getFirst().plannedTargets().getFirst().segments().getFirst().breaths());
        assertEquals(List.of(10), expected.getLast().saunaRoundsMinutes());
        assertTrue(expected.getLast().plannedTargets().isEmpty());
        assertSame(original, workout.getPlannedTargets().getFirst());
        weekly.setDays(List.of(new WorkoutPlanDay(WORKOUT_DATE.getDayOfWeek(), true, "Rest", List.of())));
        assertTrue(service.getContext(user, WORKOUT_DATE, null).weeklyPlan().day().rest());
        assertEquals(List.of(original), context.workout().sessions().getFirst().plannedTargets());
    }

    @Test
    void multipleLifecycleCandidatesAreAmbiguousAndDoNotExposeAnExpectedDay() {
        givenCurrentContext();
        when(workoutPlanRepository.findAssessmentCandidates(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq(WORKOUT_DATE), any(), any()))
            .thenReturn(List.of(weeklyPlan(List.of()), weeklyPlan(List.of())));
        var evidence = service.getContext(user, WORKOUT_DATE, null).weeklyPlan();
        assertEquals("AMBIGUOUS", evidence.evidenceStatus());
        assertNull(evidence.day());
    }

    @Test
    void canonicalKeysDistinguishNameUnitsSupersetMembershipAndOrderedSaunaRounds() {
        var segment = new WorkoutPlanDay.Segment(null, 30, null, null, null, null, null, null, null);
        String group = "11111111-1111-1111-1111-111111111111";
        String renamed = "22222222-2222-2222-2222-222222222222";
        var seconds = holdTarget("First hold", StretchingUnit.SECONDS, segment, group);
        var companion = holdTarget("Second hold", StretchingUnit.SECONDS, segment, group);
        var breaths = holdTarget("First hold", StretchingUnit.BREATHS,
            new WorkoutPlanDay.Segment(null, null, null, null, null, null, null, null, 6), group);
        String key = WorkoutPlanSessionEvidence.key(null, List.of(seconds, companion), List.of(12, 8));
        assertEquals(key, WorkoutPlanSessionEvidence.key(null, List.of(
            holdTarget("Second hold", StretchingUnit.SECONDS, segment, renamed),
            holdTarget("First hold", StretchingUnit.SECONDS, segment, renamed)), List.of(12, 8)));
        assertFalse(key.equals(WorkoutPlanSessionEvidence.key("Evening", List.of(seconds, companion), List.of(12, 8))));
        assertFalse(key.equals(WorkoutPlanSessionEvidence.key(null, List.of(breaths, companion), List.of(12, 8))));
        assertFalse(key.equals(WorkoutPlanSessionEvidence.key(null, List.of(
            holdTarget("First hold", StretchingUnit.SECONDS, segment, null),
            holdTarget("Second hold", StretchingUnit.SECONDS, segment, null)), List.of(12, 8))));
        assertFalse(key.equals(WorkoutPlanSessionEvidence.key(null, List.of(seconds, companion), List.of(8, 12))));
    }

    private WorkoutPlanTargetSnapshot holdTarget(String name, StretchingUnit unit, WorkoutPlanDay.Segment segment, String group) {
        return new WorkoutPlanTargetSnapshot(name, "Hold", ExerciseTrackingMode.SECONDS, ExerciseType.STRETCHING, null, unit, List.of(segment), group);
    }

    @Test
    void changingAnApplicableWeeklyVersionOrCreatingOneRejectsTheFetchedToken() {
        givenCurrentContext();
        var weekly = weeklyPlan(List.of());
        when(workoutPlanRepository.findAssessmentCandidates(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq(WORKOUT_DATE), any(), any()))
            .thenReturn(List.of(weekly));
        var before = service.getContext(user, WORKOUT_DATE, null);
        var original = new SaveWorkoutAssessmentRequest(8, 6, "Aligned recovery.", "Recorded work.", "Keep tracking.", "Follow the plan.", before.planUpdatedAt(), before.workoutContextToken(), true);
        weekly.setUpdateToken("new-weekly-version");
        assertThrows(BadRequestException.class, () -> service.save(user, WORKOUT_DATE, null, original));
        when(workoutPlanRepository.findAssessmentCandidates(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq(WORKOUT_DATE), any(), any()))
            .thenReturn(List.of());
        var unavailable = service.getContext(user, WORKOUT_DATE, null);
        var withoutPlan = new SaveWorkoutAssessmentRequest(8, 6, "Recorded work.", "Recorded work.", "Keep tracking.", "Follow the plan.", unavailable.planUpdatedAt(), unavailable.workoutContextToken(), true);
        when(workoutPlanRepository.findAssessmentCandidates(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq(WORKOUT_DATE), any(), any()))
            .thenReturn(List.of(weekly));
        assertThrows(BadRequestException.class, () -> service.save(user, WORKOUT_DATE, null, withoutPlan));
        org.mockito.Mockito.verify(assessmentRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void legacyOmittedStretchingUnitMatchesSecondsWithoutRewritingTheRecordingSnapshot() {
        String line = "{\"exerciseName\":\"Hold\",\"exerciseDescription\":\"Hold\",\"trackingMode\":\"SECONDS\",\"exerciseType\":\"STRETCHING\",\"segments\":[{\"durationSeconds\":30}]}";
        var recorded = new com.jllado.weightcontrol.domain.WorkoutPlanTargetSnapshotConverter().convertToEntityAttribute("[" + line + "]");
        var planned = new com.jllado.weightcontrol.domain.WorkoutPlanDaysJsonConverter().convertToEntityAttribute(
            "[{\"day\":\"THURSDAY\",\"rest\":false,\"lines\":[" + line.replace("{\"exerciseName\"", "{\"exerciseId\":10,\"exerciseName\"") + "]}]");
        assertNull(recorded.getFirst().stretchingUnit());
        assertEquals(StretchingUnit.SECONDS, planned.getFirst().lines().getFirst().stretchingUnit());
        assertEquals(WorkoutPlanSessionEvidence.key(null, planned.getFirst().lines().stream().map(WorkoutPlanSessionEvidence::snapshot).toList(), List.of()),
            WorkoutPlanSessionEvidence.key(null, recorded, List.of()));
        assertNull(recorded.getFirst().stretchingUnit());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "2026-08-20T08:00:00Z,2026-08-20T08:00:00Z,,AMBIGUOUS",
        "2026-08-19T18:00:00Z,2026-08-20T08:00:00Z,,AMBIGUOUS",
        "2026-08-19T18:00:00Z,2026-08-19T18:00:00Z,2026-08-20T08:00:00Z,AMBIGUOUS",
        "2026-08-19T18:00:00Z,2026-08-21T08:00:00Z,,HISTORICAL_VERSION_UNAVAILABLE",
        "2026-08-19T22:00:00Z,2026-08-19T22:00:00Z,2026-08-20T22:00:00Z,AVAILABLE",
        "2026-08-19T18:00:00Z,2026-08-20T22:00:00Z,,HISTORICAL_VERSION_UNAVAILABLE"
    })
    void completeDayEvidenceDoesNotApplyLaterSameDayIntentionRetroactively(String created, String updated, String archived, String status) {
        givenCurrentContext();
        var weekly = weeklyPlan(List.of());
        weekly.setCreatedAt(Instant.parse(created)); weekly.setUpdatedAt(Instant.parse(updated));
        weekly.setArchivedAt(archived == null ? null : Instant.parse(archived));
        when(workoutPlanRepository.findAssessmentCandidates(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq(WORKOUT_DATE), any(), any()))
            .thenReturn(List.of(weekly));
        var evidence = service.getContext(user, WORKOUT_DATE, null).weeklyPlan();
        assertEquals(status, evidence.evidenceStatus());
        if (!status.equals("AVAILABLE")) assertNull(evidence.day());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void datedTargetsRetainAlignmentAfterAnExerciseRenameForPlannedAndManualRecordings(boolean savedSelection) {
        var target = new WorkoutPlanDay.Target(10L, "Bench press", null, ExerciseTrackingMode.REPS, ExerciseType.TRAINING,
            null, List.of(new WorkoutPlanDay.Segment(8, null, null, null, null, null, null, null, null)), StretchingUnit.SECONDS);
        var session = new WorkoutPlanDay.Session("Strength", null, List.of(target));
        var dated = WorkoutPlanEvidenceResolver.resolve(WORKOUT_DATE, List.of(weeklyPlan(List.of(session))));
        if (savedSelection) {
            workout.setPlannedSessionName(session.name());
            workout.setPlannedTargets(List.of(new WorkoutPlanTargetSnapshot("Bench press", null, ExerciseTrackingMode.REPS,
                ExerciseType.TRAINING, null, StretchingUnit.SECONDS, target.segments())));
        }
        var day = List.of(workout);
        assertEquals(10, WorkoutRatingRules.calculate(day, dated, WorkoutRatingRules.load(day), List.of()).alignment());
        workout.getLines().getFirst().getExercise().setName("Barbell bench press");
        assertEquals(10, WorkoutRatingRules.calculate(day, dated, WorkoutRatingRules.load(day), List.of()).alignment());
        workout.getLines().getFirst().setExercise(exercise(99L, "Bench press"));
        assertEquals(savedSelection ? 1 : 5, WorkoutRatingRules.calculate(day, dated, WorkoutRatingRules.load(day), List.of()).alignment());
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(dated);
        assertFalse(json.toString().contains("exerciseId"));
    }

    @Test
    void alignmentCountsCompletedTargetsAndSkippedSetsOnceAcrossSessions() {
        var target = new WorkoutPlanDay.Target(10L, "Bench press", null, ExerciseTrackingMode.REPS, ExerciseType.TRAINING,
            null, java.util.Collections.nCopies(2, new WorkoutPlanDay.Segment(8, null, BigDecimal.TEN, null, null, null, null, null, null)),
            StretchingUnit.SECONDS);
        var session = new WorkoutPlanDay.Session("Press", null, List.of(target));
        var weekly = WorkoutPlanEvidenceResolver.resolve(WORKOUT_DATE, List.of(weeklyPlan(List.of(session, session))));
        workout.setPlannedSessionName("Press");
        workout.setPlannedTargets(List.of(WorkoutPlanSessionEvidence.snapshot(target)));
        workout.getLines().getFirst().getSegments().getFirst().setWeight(BigDecimal.TEN);
        var skipped = new WorkoutSegment();
        skipped.setSkipped(true);
        workout.getLines().getFirst().getSegments().add(skipped);
        var second = workout(WORKOUT_DATE, exercise(10L, "Bench press"));
        second.setPlannedSessionName("Press");
        second.setPlannedTargets(workout.getPlannedTargets());
        var done = second.getLines().getFirst().getSegments().getFirst();
        done.setWeight(BigDecimal.TEN);
        second.getLines().getFirst().getSegments().add(done);
        var both = List.of(workout, second);
        assertEquals(8, WorkoutRatingRules.calculate(both, weekly, WorkoutRatingRules.load(both), List.of()).alignment());
        var single = List.of(workout);
        assertEquals(5, WorkoutRatingRules.calculate(single, weekly, WorkoutRatingRules.load(single), List.of()).alignment());
        assertEquals(6, WorkoutRatingRules.calculate(single, WorkoutPlanEvidenceResolver.resolve(WORKOUT_DATE, List.of()),
            WorkoutRatingRules.load(single), List.of()).alignment());
    }

    @Test
    void restRecoveryAndSkippedTrainingHaveNoDemandAndCompletedCardioUsesOnlyIntervalTime() {
        var rest = WorkoutPlanEvidenceResolver.resolve(WORKOUT_DATE, List.of(weeklyPlan(List.of())));
        var stretch = exercise(12L, "Stretch");
        stretch.setExerciseType(ExerciseType.STRETCHING);
        var warmup = exercise(13L, "Prepare");
        warmup.setExerciseType(ExerciseType.WARM_UP);
        var cardio = exercise(14L, "Running");
        cardio.setTrackingMode(ExerciseTrackingMode.CARDIO);
        var recovery = workout(WORKOUT_DATE, stretch, warmup, cardio);
        recovery.setSaunaSession(true);
        recovery.setSaunaRoundsMinutes(List.of(30));
        recovery.setDurationMinutes(90);
        var interval = recovery.getLines().getLast().getSegments().getFirst();
        interval.setSkipped(true);
        var day = List.of(recovery);
        var rated = WorkoutRatingRules.calculate(day, rest, WorkoutRatingRules.load(day), List.of());
        assertEquals(10, rated.alignment());
        assertEquals(1, rated.demand());
        interval.setSkipped(false);
        interval.setRepetitions(null);
        interval.setDurationSeconds(900);
        var withCardio = WorkoutRatingRules.calculate(day, rest, WorkoutRatingRules.load(day), List.of());
        assertEquals(3, withCardio.alignment());
        assertEquals(3, withCardio.demand());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"6,1", "7,2", "8,2", "9,2", "10,3"})
    void relativeDemandUsesInclusiveSeventyFiveAndOneHundredTwentyFivePercentThresholds(int reps, int expected) {
        var previous = workout(WORKOUT_DATE.minusDays(1), exercise(10L, "Bench press"));
        workout.getLines().getFirst().getSegments().getFirst().setRepetitions(reps);
        var today = List.of(workout);
        var unavailable = WorkoutPlanEvidenceResolver.resolve(WORKOUT_DATE, List.of());
        var result = WorkoutRatingRules.calculate(today, unavailable, WorkoutRatingRules.load(today), List.of(List.of(previous)));
        assertEquals(expected, result.demand());
        assertEquals(5, result.alignment());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "25,4.60,3,125%", "25,4.59,2,124.73%", "25,4.61,3,125.27%",
        "15,4.60,1,75%", "15,4.59,1,74.84%", "15,4.61,2,75.16%"
    })
    void demandComparesExactDecimalVolumes(int reps, BigDecimal weight, int score, String percentage) {
        var previous = workout(WORKOUT_DATE.minusDays(1), exercise(10L, "Bench press"));
        var baseline = previous.getLines().getFirst().getSegments().getFirst();
        baseline.setRepetitions(20);
        baseline.setWeight(new BigDecimal("4.60"));
        var current = workout.getLines().getFirst().getSegments().getFirst();
        current.setRepetitions(reps);
        current.setWeight(weight);
        var day = List.of(workout);
        var unavailable = WorkoutPlanEvidenceResolver.resolve(WORKOUT_DATE, List.of());
        var result = WorkoutRatingRules.calculate(day, unavailable, WorkoutRatingRules.load(day), List.of(List.of(previous)));
        assertEquals(score, result.demand());
        assertTrue(result.improvement().startsWith(percentage + " of"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "124999,0,Just under 125%", "125001,1,Just over 125%",
        "74999,-1,Just under 75%", "75001,0,Just over 75%"
    })
    void percentageRoundingPreservesWhichSideOfTheThresholdTheScoreUsed(int numerator, int adjustment, String percentage) {
        var ratio = new WorkoutLoadRatio(java.math.BigInteger.valueOf(numerator), java.math.BigInteger.valueOf(100000));
        assertEquals(adjustment, ratio.adjustment());
        assertEquals(percentage, ratio.percentage());
    }

    @Test
    void demandAveragesRecurringFractionsExactlyAcrossExercisesAndDays() {
        var bench = exercise(10L, "Bench press");
        var row = exercise(11L, "Row");
        var day = List.of(workout(WORKOUT_DATE, bench, row));
        var first = workout(WORKOUT_DATE.minusDays(1), bench, row);
        var second = workout(WORKOUT_DATE.minusDays(2), bench);
        day.getFirst().getLines().get(0).getSegments().getFirst().setRepetitions(2);
        day.getFirst().getLines().get(1).getSegments().getFirst().setRepetitions(7);
        first.getLines().get(0).getSegments().getFirst().setRepetitions(3);
        first.getLines().get(1).getSegments().getFirst().setRepetitions(3);
        second.getLines().getFirst().getSegments().getFirst().setRepetitions(2);
        // Daily means are (2/3 + 7/3)/2 and 2/2, giving an exact overall ratio of 5/4.
        var result = WorkoutRatingRules.calculate(day, WorkoutPlanEvidenceResolver.resolve(WORKOUT_DATE, List.of()),
            WorkoutRatingRules.load(day), List.of(List.of(first), List.of(second)));
        assertEquals(3, result.demand());
        assertTrue(result.improvement().startsWith("125% of"));
    }

    @Test
    void alignmentHonorsPositiveCardioTargetsAndNeverTreatsFutureGoalsAsHistorical() {
        var run = exercise(22L, "Bike");
        run.setTrackingMode(ExerciseTrackingMode.CARDIO);
        workout = workout(WORKOUT_DATE, run);
        var interval = workout.getLines().getFirst().getSegments().getFirst();
        interval.setRepetitions(null);
        interval.setDurationSeconds(600);
        interval.setSpeedKph(BigDecimal.valueOf(5));
        var target = new WorkoutPlanTargetSnapshot("Bike", null, ExerciseTrackingMode.CARDIO, ExerciseType.TRAINING, null, null,
            List.of(new WorkoutPlanDay.Segment(null, 600, null, BigDecimal.TEN, null, null, null, null, null)));
        workout.setPlannedTargets(List.of(target));
        workout.setPlannedSaunaRoundsMinutes(List.of(10));
        workout.setSaunaSession(true);
        workout.setSaunaRoundsMinutes(List.of(10));
        givenCurrentContext();
        plan.setUpdatedAt(WORKOUT_DATE.plusDays(1).atStartOfDay(com.jllado.weightcontrol.util.DateTimes.USER_ZONE).toInstant());
        service.refresh(user, WORKOUT_DATE);
        var stored = org.mockito.ArgumentCaptor.forClass(WorkoutAssessment.class);
        verify(assessmentRepository).save(stored.capture());
        assertEquals(8, stored.getValue().getGoalAlignmentScore());
        assertEquals(2, stored.getValue().getEstimatedTrainingDemandScore());
        assertEquals("Recorded session targets", stored.getValue().getGoalSnapshot());
        assertNull(stored.getValue().getPlanUpdatedAt());
        assertNull(service.getContext(user, WORKOUT_DATE, null).activePlan());
    }

    private WorkoutPlan weeklyPlan(List<WorkoutPlanDay.Session> sessions) {
        var weekly = new WorkoutPlan();
        weekly.setId(100L); weekly.setUser(user); weekly.setStartDate(WORKOUT_DATE.minusDays(7)); weekly.setReviewDate(WORKOUT_DATE.plusDays(7));
        weekly.setCreatedAt(WORKOUT_UPDATED_AT.minusSeconds(86400)); weekly.setUpdatedAt(WORKOUT_UPDATED_AT.minusSeconds(86400)); weekly.setUpdateToken("weekly-version");
        weekly.setDays(java.util.Arrays.stream(java.time.DayOfWeek.values())
            .map(day -> new WorkoutPlanDay(day, day != WORKOUT_DATE.getDayOfWeek() || sessions.isEmpty(), day == WORKOUT_DATE.getDayOfWeek() ? sessions : List.of())).toList());
        return weekly;
    }

    private void givenCurrentContext() {
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(workout));
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.of(plan));
    }

    private SaveWorkoutAssessmentRequest request(boolean confirmed, int score, String rationale) {
        return new SaveWorkoutAssessmentRequest(
            score,
            7,
            rationale,
            "Consistent compound work.",
            "Add one pulling set.",
            "Repeat with controlled progression.",
            PLAN_UPDATED_AT,
            WorkoutAssessmentService.contextToken(List.of(workout)),
            confirmed
        );
    }

    private Workout workout(LocalDate date, Exercise... exercises) {
        Workout result = new Workout();
        result.setUser(user);
        result.setWorkoutDate(date);
        for (int i = 0; i < exercises.length; i++) {
            WorkoutLine line = new WorkoutLine();
            line.setWorkout(result);
            line.setExercise(exercises[i]);
            line.setPosition(i);
            WorkoutSegment segment = new WorkoutSegment();
            segment.setWorkoutLine(line);
            segment.setPosition(0);
            segment.setRepetitions(8);
            line.getSegments().add(segment);
            result.getLines().add(line);
        }
        return result;
    }

    private Exercise exercise(long id, String name) {
        Exercise exercise = new Exercise();
        exercise.setExerciseType(ExerciseType.TRAINING);
        exercise.setId(id);
        exercise.setName(name);
        exercise.setDescription(name + " description");
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        return exercise;
    }

    private WorkoutAssessment assessment(Workout assessedWorkout, int score) {
        WorkoutAssessment assessment = new WorkoutAssessment();
        assessment.setUser(assessedWorkout.getUser());
        assessment.setWorkoutDate(assessedWorkout.getWorkoutDate());
        assessment.setGoalAlignmentScore(score);
        assessment.setEstimatedTrainingDemandScore(7);
        assessment.setRationale("Clear alignment with the active goal.");
        assessment.setStrength("Consistent compound work.");
        assessment.setImprovement("Add one pulling set.");
        assessment.setNextWorkoutAction("Repeat with controlled progression.");
        assessment.setGoalSnapshot("Improve upper-body strength");
        assessment.setPlanUpdatedAt(PLAN_UPDATED_AT);
        assessment.setCreatedAt(WORKOUT_UPDATED_AT);
        assessment.setUpdatedAt(WORKOUT_UPDATED_AT);
        return assessment;
    }
}
