package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.CoachDtos;
import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachingPlanResponse;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentWorkoutData;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.SaveWorkoutAssessmentRequest;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentContextResponse;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentResponse;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutAssessment;
import com.jllado.weightcontrol.repository.CoachingPlanRepository;
import com.jllado.weightcontrol.repository.HealthConstraintRepository;
import com.jllado.weightcontrol.repository.WorkoutAssessmentRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class WorkoutAssessmentService {

    private static final int COMPARISON_DAYS = 90;
    private static final int COMPARISON_LIMIT = 10;

    private final WorkoutRepository workoutRepository;
    private final WorkoutAssessmentRepository assessmentRepository;
    private final CoachingPlanRepository coachingPlanRepository;
    private final HealthConstraintRepository healthConstraintRepository;

    public WorkoutAssessmentService(
        WorkoutRepository workoutRepository,
        WorkoutAssessmentRepository assessmentRepository,
        CoachingPlanRepository coachingPlanRepository,
        HealthConstraintRepository healthConstraintRepository
    ) {
        this.workoutRepository = workoutRepository;
        this.assessmentRepository = assessmentRepository;
        this.coachingPlanRepository = coachingPlanRepository;
        this.healthConstraintRepository = healthConstraintRepository;
    }

    public WorkoutAssessmentContextResponse getContext(User user, LocalDate workoutDate) {
        Workout workout = requireWorkout(user, workoutDate);
        CoachingPlan plan = requirePlan(user);
        Set<Long> exerciseIds = workout.getLines().stream()
            .map(line -> line.getExercise().getId())
            .collect(Collectors.toSet());
        List<AssessmentWorkoutData> comparableTraining = workoutRepository
            .findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, workoutDate.minusDays(COMPARISON_DAYS), workoutDate.minusDays(1))
            .stream()
            .filter(candidate -> candidate.getLines().stream().anyMatch(line -> exerciseIds.contains(line.getExercise().getId())))
            .sorted(Comparator.comparing(Workout::getWorkoutDate).reversed())
            .limit(COMPARISON_LIMIT)
            .peek(this::initializeSegments)
            .map(candidate -> AssessmentWorkoutData.comparable(candidate, exerciseIds))
            .toList();
        LocalDate today = LocalDate.now(DateTimes.USER_ZONE);
        return new WorkoutAssessmentContextResponse(
            AssessmentWorkoutData.from(workout),
            CoachingPlanResponse.from(plan),
            healthConstraintRepository.findActiveOverlapping(user, today, today).stream()
                .map(this::toHealthConstraintData)
                .toList(),
            comparableTraining,
            workout.getAssessment() == null ? null : WorkoutAssessmentResponse.from(workout.getAssessment(), workout),
            plan.getUpdatedAt(),
            workout.getUpdatedAt()
        );
    }

    public WorkoutAssessmentResponse save(User user, LocalDate workoutDate, SaveWorkoutAssessmentRequest request) {
        if (!request.confirmed()) {
            throw new BadRequestException("Workout assessment write requires explicit confirmation");
        }
        validateScore(request.goalAlignmentScore(), "Goal-alignment score");
        validateScore(request.estimatedTrainingDemandScore(), "Estimated training-demand score");
        validateWordCount(request.rationale(), 25, "Rationale");
        validateWordCount(request.strength(), 15, "Strength");
        validateWordCount(request.improvement(), 15, "Improvement");
        validateWordCount(request.nextWorkoutAction(), 15, "Next-workout action");
        Workout workout = requireWorkout(user, workoutDate);
        CoachingPlan plan = requirePlan(user);
        if (!request.workoutUpdatedAt().equals(workout.getUpdatedAt()) || !request.planUpdatedAt().equals(plan.getUpdatedAt())) {
            throw new BadRequestException("Workout assessment context is stale; reload it before reassessing");
        }
        WorkoutAssessment assessment = workout.getAssessment();
        if (assessment == null) {
            assessment = new WorkoutAssessment();
            assessment.setWorkout(workout);
            workout.setAssessment(assessment);
        }
        assessment.setGoalAlignmentScore(request.goalAlignmentScore());
        assessment.setEstimatedTrainingDemandScore(request.estimatedTrainingDemandScore());
        assessment.setRationale(request.rationale());
        assessment.setStrength(request.strength());
        assessment.setImprovement(request.improvement());
        assessment.setNextWorkoutAction(request.nextWorkoutAction());
        assessment.setGoalSnapshot(plan.getGoal());
        assessment.setPlanUpdatedAt(plan.getUpdatedAt());
        assessment.setWorkoutUpdatedAt(workout.getUpdatedAt());
        return WorkoutAssessmentResponse.from(assessmentRepository.saveAndFlush(assessment), workout);
    }

    private Workout requireWorkout(User user, LocalDate workoutDate) {
        Workout workout = workoutRepository.findWithLinesByUserAndWorkoutDate(user, workoutDate)
            .orElseThrow(() -> new NotFoundException("Workout not found"));
        initializeSegments(workout);
        return workout;
    }

    private CoachingPlan requirePlan(User user) {
        return coachingPlanRepository.findByUser(user)
            .orElseThrow(() -> new BadRequestException("An active coaching plan is required before assessing a workout"));
    }

    private void initializeSegments(Workout workout) {
        workout.getLines().forEach(line -> line.getSegments().size());
    }

    private void validateWordCount(String value, int maximum, String name) {
        int words = value.trim().split("\\s+").length;
        if (words > maximum) {
            throw new BadRequestException(name + " must contain no more than " + maximum + " words");
        }
    }

    private void validateScore(int value, String name) {
        if (value < 1 || value > 10) {
            throw new BadRequestException(name + " must be between 1 and 10");
        }
    }

    private CoachDtos.HealthConstraintData toHealthConstraintData(HealthConstraint constraint) {
        return new CoachDtos.HealthConstraintData(
            constraint.getType(),
            constraint.getTitle(),
            constraint.getDetails(),
            constraint.getSource(),
            constraint.getStartDate(),
            constraint.getEndDate()
        );
    }
}
