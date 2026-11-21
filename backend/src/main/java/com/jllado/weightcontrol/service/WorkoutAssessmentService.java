package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.CoachDtos;
import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachingPlanResponse;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentWorkoutData;
import com.jllado.weightcontrol.domain.WorkoutPlan;
import com.jllado.weightcontrol.repository.WorkoutPlanRepository;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.SaveWorkoutAssessmentRequest;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentContextResponse;
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.WorkoutAssessmentResponse;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.ExerciseType;
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
import com.jllado.weightcontrol.api.dto.WorkoutAssessmentDtos.AssessmentDayData;
import com.jllado.weightcontrol.repository.UserRepository;

@Service
@Transactional
public class WorkoutAssessmentService {

    private static final int COMPARISON_DAYS = 90;
    private static final int COMPARISON_LIMIT = 3;

    private final WorkoutRepository workoutRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final UserRepository userRepository;
    private final WorkoutAssessmentRepository assessmentRepository;
    private final CoachingPlanRepository coachingPlanRepository;
    private final HealthConstraintRepository healthConstraintRepository;

    public WorkoutAssessmentService(
        WorkoutRepository workoutRepository,
        WorkoutAssessmentRepository assessmentRepository,
        CoachingPlanRepository coachingPlanRepository,
        HealthConstraintRepository healthConstraintRepository,
        UserRepository userRepository,
        WorkoutPlanRepository workoutPlanRepository
    ) {
        this.workoutRepository = workoutRepository;
        this.workoutPlanRepository = workoutPlanRepository;
        this.userRepository = userRepository;
        this.assessmentRepository = assessmentRepository;
        this.coachingPlanRepository = coachingPlanRepository;
        this.healthConstraintRepository = healthConstraintRepository;
    }

    public WorkoutAssessmentContextResponse getContext(User user, LocalDate workoutDate, String sessionReference) {
        List<Workout> sessions = requireDay(user, workoutDate, sessionReference);
        var plan = datedGoal(user, workoutDate);
        Set<Long> exerciseIds = sessions.stream().flatMap(workout -> workout.getLines().stream())
            .filter(line -> line.getExercise().getExerciseType() == ExerciseType.TRAINING)
            .map(line -> line.getExercise().getId())
            .collect(Collectors.toSet());
        List<AssessmentDayData> comparableTraining = workoutRepository
            .findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(user, workoutDate.minusDays(COMPARISON_DAYS), workoutDate.minusDays(1))
            .stream()
            .collect(Collectors.groupingBy(Workout::getWorkoutDate)).entrySet().stream()
            .filter(entry -> entry.getValue().stream().flatMap(candidate -> candidate.getLines().stream()).anyMatch(line -> exerciseIds.contains(line.getExercise().getId())))
            .sorted(java.util.Map.Entry.<LocalDate, List<Workout>>comparingByKey().reversed())
            .limit(COMPARISON_LIMIT)
            .map(entry -> new AssessmentDayData(entry.getKey(), entry.getValue().stream()
                .filter(candidate -> candidate.getLines().stream().anyMatch(line -> exerciseIds.contains(line.getExercise().getId())))
                .peek(this::initializeSegments).map(candidate -> AssessmentWorkoutData.comparable(candidate, exerciseIds)).toList()))
            .toList();
        var weeklyPlans = weeklyPlans(user, workoutDate);
        return new WorkoutAssessmentContextResponse(
            new AssessmentDayData(workoutDate, sessions.stream().map(AssessmentWorkoutData::from).toList()),
            WorkoutPlanEvidenceResolver.resolve(workoutDate, weeklyPlans),
            plan.map(CoachingPlanResponse::from).orElse(null),
            healthConstraintRepository.findActiveOverlapping(user, workoutDate, workoutDate).stream()
                .map(this::toHealthConstraintData)
                .toList(),
            comparableTraining,
            assessmentRepository.findByUserAndWorkoutDate(user, workoutDate).map(WorkoutAssessmentResponse::from).orElse(null),
            plan.map(CoachingPlan::getUpdatedAt).orElse(null),
            contextToken(sessions, weeklyPlans)
        );
    }

    public WorkoutAssessmentResponse save(User user, LocalDate workoutDate, String sessionReference, SaveWorkoutAssessmentRequest request) {
        if (!request.confirmed()) {
            throw new BadRequestException("Workout assessment write requires explicit confirmation");
        }
        validateScore(request.goalAlignmentScore(), "Goal-alignment score");
        validateScore(request.estimatedTrainingDemandScore(), "Estimated training-demand score");
        validateWordCount(request.rationale(), 25, "Rationale");
        validateWordCount(request.strength(), 15, "Strength");
        validateWordCount(request.improvement(), 15, "Improvement");
        validateWordCount(request.nextWorkoutAction(), 15, "Next-workout action");
        List<Workout> sessions = requireDay(user, workoutDate, sessionReference);
        var planUpdatedAt = datedGoal(user, workoutDate).map(CoachingPlan::getUpdatedAt).orElse(null);
        if (!request.workoutContextToken().equals(contextToken(sessions, weeklyPlans(user, workoutDate)))
            || !java.util.Objects.equals(request.planUpdatedAt(), planUpdatedAt)) {
            throw new BadRequestException("Workout assessment context is stale; reload it before reading the assessment");
        }
        return assessmentRepository.findByUserAndWorkoutDate(user, workoutDate).map(WorkoutAssessmentResponse::from)
            .orElseThrow(() -> new NotFoundException("No saved assessment for this workout day"));
    }

    /** Workout mutations already hold the owner's write lock. */
    void refresh(User user, LocalDate date) {
        var sessions = workoutRepository.findSessionsOnDate(user, date);
        if (sessions.isEmpty()) {
            assessmentRepository.deleteByUserAndWorkoutDate(user, date);
            return;
        }
        var weekly = WorkoutPlanEvidenceResolver.resolve(date, weeklyPlans(user, date));
        var load = WorkoutRatingRules.load(sessions);
        var rating = WorkoutRatingRules.calculate(sessions, weekly, load, priorDays(user, date, load));
        var assessment = assessmentRepository.findByUserAndWorkoutDate(user, date).orElseGet(WorkoutAssessment::new);
        assessment.setAutomatic(true);
        assessment.setUser(user);
        assessment.setWorkoutDate(date);
        assessment.setGoalAlignmentScore(rating.alignment());
        assessment.setEstimatedTrainingDemandScore(rating.demand());
        assessment.setRationale(rating.rationale());
        assessment.setStrength(rating.strength());
        assessment.setImprovement(rating.improvement());
        assessment.setNextWorkoutAction(rating.action());
        assessment.setGoalSnapshot(datedGoal(user, date).map(CoachingPlan::getGoal).orElse(rating.goal()));
        assessment.setPlanUpdatedAt(weekly.day() == null ? null : weekly.updatedAt());
        assessmentRepository.save(assessment);
    }

    private List<List<Workout>> priorDays(User user, LocalDate date, WorkoutRatingRules.Load load) {
        var weighted = load.exerciseIds(true);
        var unweighted = load.exerciseIds(false);
        if (weighted.isEmpty() && unweighted.isEmpty()) return List.of();
        var dates = workoutRepository.findComparableDates(user, date.minusDays(COMPARISON_DAYS), date.minusDays(1),
            weighted, unweighted, org.springframework.data.domain.PageRequest.of(0, COMPARISON_LIMIT));
        if (dates.isEmpty()) return List.of();
        var workouts = workoutRepository.findByUserAndWorkoutDateIn(user, dates).stream()
            .collect(Collectors.groupingBy(Workout::getWorkoutDate));
        return dates.stream().map(workouts::get).toList();
    }

    private List<Workout> requireDay(User user, LocalDate workoutDate, String sessionReference) {
        if (sessionReference != null) throw new BadRequestException("Assessments now cover the whole day; reload context by date without sessionReference");
        userRepository.findByIdForUpdate(user.getId()).orElseThrow();
        List<Workout> sessions = workoutRepository.findSessionsOnDate(user, workoutDate);
        if (sessions.isEmpty()) throw new NotFoundException("Workout day not found");
        sessions.forEach(this::initializeSegments);
        return sessions;
    }

    static String contextToken(List<Workout> sessions) {
        String snapshot = sessions.stream().sorted(Comparator.comparing(Workout::getSessionReference))
            .map(workout -> workout.getSessionReference() + ":" + workout.getWorkoutDate() + ":" + workout.getUpdatedAt())
            .collect(Collectors.joining("|"));
        return org.springframework.util.DigestUtils.md5DigestAsHex(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private List<WorkoutPlan> weeklyPlans(User user, LocalDate date) {
        return workoutPlanRepository.findAssessmentCandidates(user, date, date.atStartOfDay(DateTimes.USER_ZONE).toInstant(),
            date.plusDays(1).atStartOfDay(DateTimes.USER_ZONE).toInstant());
    }

    private String contextToken(List<Workout> sessions, List<WorkoutPlan> plans) {
        if (plans.isEmpty()) return contextToken(sessions);
        String snapshot = contextToken(sessions) + plans.stream().map(plan -> "|" + plan.getId() + ":" + plan.getUpdateToken()
            + ":" + plan.getUpdatedAt() + ":" + plan.getArchivedAt()).collect(Collectors.joining());
        return org.springframework.util.DigestUtils.md5DigestAsHex(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private java.util.Optional<CoachingPlan> datedGoal(User user, LocalDate date) {
        var dayEnd = date.plusDays(1).atStartOfDay(DateTimes.USER_ZONE).toInstant();
        return coachingPlanRepository.findByUser(user).filter(plan -> !plan.getStartDate().isAfter(date) && plan.getUpdatedAt().isBefore(dayEnd));
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
