package com.jllado.weightcontrol.service;

import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordAchievementResponse;

import com.jllado.weightcontrol.api.dto.WeightDtos.WeightRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutRequest;
import com.jllado.weightcontrol.domain.*;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PersonalRecordMutationService {

    private final PersonalRecordService personalRecordService;
    private final WeightService weightService;
    private final WorkoutService workoutService;
    private final DashboardService dashboardService;

    public PersonalRecordMutationService(
        PersonalRecordService personalRecordService,
        WeightService weightService,
        WorkoutService workoutService,
        DashboardService dashboardService
    ) {
        this.personalRecordService = personalRecordService;
        this.weightService = weightService;
        this.workoutService = workoutService;
        this.dashboardService = dashboardService;
    }

    public MutationResult<Weight> createWeight(User user, WeightRequest request) {
        var previous = personalRecordService.captureCurrentValues(user);
        Weight result = weightService.create(user, request);
        dashboardService.refreshCurrentStatus(user);
        var achievements = personalRecordService.rebuildAndFindAchievements(user, previous, PersonalRecordSourceType.WEIGHT, result.getId(), true);
        return new MutationResult<>(result, achievements);
    }

    public MutationResult<Weight> updateWeight(User user, Long id, WeightRequest request) {
        Weight result = weightService.update(user, id, request);
        dashboardService.refreshCurrentStatus(user);
        personalRecordService.rebuild(user);
        return new MutationResult<>(result, List.of());
    }

    public void deleteWeight(User user, Long id) {
        weightService.delete(user, id);
        dashboardService.refreshCurrentStatus(user);
        personalRecordService.rebuild(user);
    }

    public MutationResult<Workout> createWorkout(User user, WorkoutRequest request) {
        var previous = personalRecordService.captureCurrentValues(user);
        Workout result = workoutService.create(user, request);
        var achievements = personalRecordService.rebuildAndFindAchievements(user, previous, PersonalRecordSourceType.WORKOUT, result.getId(), true);
        return new MutationResult<>(result, achievements);
    }

    public MutationResult<Workout> updateWorkout(User user, Long id, WorkoutRequest request) {
        Workout result = workoutService.update(user, id, request);
        personalRecordService.rebuild(user);
        return new MutationResult<>(result, List.of());
    }

    public void deleteWorkout(User user, Long id) {
        workoutService.delete(user, id);
        personalRecordService.rebuild(user);
    }

    public record MutationResult<T>(T result, List<RecordAchievementResponse> achievements) {
    }
}
