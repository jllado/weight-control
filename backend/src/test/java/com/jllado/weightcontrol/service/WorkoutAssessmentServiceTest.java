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
    void confirmedSaveCreatesAssessmentAndDerivesTheGoalSnapshot() {
        givenCurrentContext();
        when(assessmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.save(user, WORKOUT_DATE, null, request(true, 8, "Clear alignment with the active goal."));

        verify(assessmentRepository).saveAndFlush(any());
        assertEquals("Improve upper-body strength", response.goalSnapshot());
        assertEquals(8, response.goalAlignmentScore());
        var saved = org.mockito.ArgumentCaptor.forClass(WorkoutAssessment.class);
        verify(assessmentRepository).saveAndFlush(saved.capture());
        assertSame(user, saved.getValue().getUser());
        assertEquals(WORKOUT_DATE, saved.getValue().getWorkoutDate());
        assertEquals(PLAN_UPDATED_AT, saved.getValue().getPlanUpdatedAt());
    }

    @Test
    void reassessmentAtomicallyUpdatesTheExistingRow() {
        WorkoutAssessment existing = assessment(workout, 5);
        when(assessmentRepository.findByUserAndWorkoutDate(user, WORKOUT_DATE)).thenReturn(Optional.of(existing));
        givenCurrentContext();
        when(assessmentRepository.saveAndFlush(existing)).thenReturn(existing);

        service.save(user, WORKOUT_DATE, null, request(true, 9, "Improved alignment after the revised session."));

        verify(assessmentRepository).saveAndFlush(existing);
        assertEquals(9, existing.getGoalAlignmentScore());
        assertEquals("Improved alignment after the revised session.", existing.getRationale());
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
    void contextRequiresAnOwnedWorkoutAndAnActivePlan() {
        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of());
        assertThrows(NotFoundException.class, () -> service.getContext(user, WORKOUT_DATE, null));

        when(workoutRepository.findSessionsOnDate(user, WORKOUT_DATE)).thenReturn(List.of(workout));
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.getContext(user, WORKOUT_DATE, null));
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
