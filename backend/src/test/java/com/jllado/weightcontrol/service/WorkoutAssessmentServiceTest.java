package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.SaveWorkoutAssessmentRequest;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.Exercise;
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
    private WorkoutAssessmentRepository assessmentRepository;
    @Mock
    private CoachingPlanRepository coachingPlanRepository;
    @Mock
    private HealthConstraintRepository healthConstraintRepository;

    @InjectMocks
    private WorkoutAssessmentService service;

    private User user;
    private Workout workout;
    private CoachingPlan plan;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        workout = workout(WORKOUT_DATE, exercise(10L, "Bench press"));
        workout.setUpdatedAt(WORKOUT_UPDATED_AT);
        plan = new CoachingPlan();
        plan.setUser(user);
        plan.setGoal("Improve upper-body strength");
        plan.setUpdatedAt(PLAN_UPDATED_AT);
    }

    @Test
    void contextReturnsOnlyMatchingPriorTrainingNewestFirstWithoutIdentifiers() {
        Workout newestMatch = workout(WORKOUT_DATE.minusDays(4), exercise(10L, "Bench press"), exercise(30L, "Squat"));
        Workout olderMatch = workout(WORKOUT_DATE.minusDays(40), exercise(10L, "Bench press"));
        Workout nonMatch = workout(WORKOUT_DATE.minusDays(2), exercise(20L, "Running"));
        when(workoutRepository.findWithLinesByUserAndWorkoutDate(user, WORKOUT_DATE)).thenReturn(Optional.of(workout));
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.of(plan));
        when(workoutRepository.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(
            user,
            WORKOUT_DATE.minusDays(90),
            WORKOUT_DATE.minusDays(1)
        )).thenReturn(List.of(olderMatch, nonMatch, newestMatch));
        when(healthConstraintRepository.findActiveOverlapping(any(), any(), any())).thenReturn(List.of());

        var context = service.getContext(user, WORKOUT_DATE);

        assertEquals(WORKOUT_DATE, context.workout().date());
        assertEquals(List.of(WORKOUT_DATE.minusDays(4), WORKOUT_DATE.minusDays(40)),
            context.recentComparableTraining().stream().map(item -> item.date()).toList());
        assertEquals(List.of("Bench press"),
            context.recentComparableTraining().getFirst().lines().stream().map(item -> item.exercise()).toList());
        assertEquals("Improve upper-body strength", context.activePlan().goal());
        assertEquals(WORKOUT_UPDATED_AT, context.workoutUpdatedAt());
        assertEquals(PLAN_UPDATED_AT, context.planUpdatedAt());
    }

    @Test
    void confirmedSaveCreatesAssessmentAndDerivesTheGoalSnapshot() {
        givenCurrentContext();
        when(assessmentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.save(user, WORKOUT_DATE, request(true, 8, "Clear alignment with the active goal."));

        verify(assessmentRepository).saveAndFlush(any());
        assertEquals("Improve upper-body strength", response.goalSnapshot());
        assertEquals(8, response.goalAlignmentScore());
        assertSame(workout, workout.getAssessment().getWorkout());
        assertEquals(PLAN_UPDATED_AT, workout.getAssessment().getPlanUpdatedAt());
    }

    @Test
    void reassessmentAtomicallyUpdatesTheExistingRow() {
        WorkoutAssessment existing = assessment(workout, 5);
        workout.setAssessment(existing);
        givenCurrentContext();
        when(assessmentRepository.saveAndFlush(existing)).thenReturn(existing);

        service.save(user, WORKOUT_DATE, request(true, 9, "Improved alignment after the revised session."));

        verify(assessmentRepository).saveAndFlush(existing);
        assertEquals(9, existing.getGoalAlignmentScore());
        assertEquals("Improved alignment after the revised session.", existing.getRationale());
    }

    @Test
    void saveRejectsMissingConfirmationInvalidScoresAndLongText() {
        assertThrows(BadRequestException.class,
            () -> service.save(user, WORKOUT_DATE, request(false, 8, "Valid rationale.")));
        assertThrows(BadRequestException.class,
            () -> service.save(user, WORKOUT_DATE, request(true, 11, "Valid rationale.")));
        String longRationale = String.join(" ", java.util.Collections.nCopies(26, "word"));
        assertThrows(BadRequestException.class,
            () -> service.save(user, WORKOUT_DATE, request(true, 8, longRationale)));
    }

    @Test
    void saveRejectsStaleWorkoutOrPlanContext() {
        givenCurrentContext();

        assertThrows(BadRequestException.class, () -> service.save(
            user,
            WORKOUT_DATE,
            new SaveWorkoutAssessmentRequest(
                8,
                7,
                "Valid rationale.",
                "Clear strength.",
                "Small improvement.",
                "Repeat next week.",
                PLAN_UPDATED_AT.plusSeconds(1),
                WORKOUT_UPDATED_AT,
                true
            )
        ));
    }

    @Test
    void contextRequiresAnOwnedWorkoutAndAnActivePlan() {
        when(workoutRepository.findWithLinesByUserAndWorkoutDate(user, WORKOUT_DATE)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getContext(user, WORKOUT_DATE));

        when(workoutRepository.findWithLinesByUserAndWorkoutDate(user, WORKOUT_DATE)).thenReturn(Optional.of(workout));
        when(coachingPlanRepository.findByUser(user)).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.getContext(user, WORKOUT_DATE));
    }

    @Test
    private void givenCurrentContext() {
        when(workoutRepository.findWithLinesByUserAndWorkoutDate(user, WORKOUT_DATE)).thenReturn(Optional.of(workout));
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
            WORKOUT_UPDATED_AT,
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
        exercise.setId(id);
        exercise.setName(name);
        exercise.setDescription(name + " description");
        exercise.setTrackingMode(ExerciseTrackingMode.REPS);
        return exercise;
    }

    private WorkoutAssessment assessment(Workout assessedWorkout, int score) {
        WorkoutAssessment assessment = new WorkoutAssessment();
        assessment.setWorkout(assessedWorkout);
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
