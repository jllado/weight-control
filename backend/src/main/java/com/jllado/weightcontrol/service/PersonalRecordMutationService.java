package com.jllado.weightcontrol.service;

import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordAchievementResponse;

import com.jllado.weightcontrol.api.dto.WeightDtos.WeightRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutRequest;
import com.jllado.weightcontrol.api.dto.BloodPressureDtos.BloodPressureRequest;
import com.jllado.weightcontrol.api.dto.LipidPanelDtos.LipidPanelRequest;
import com.jllado.weightcontrol.api.dto.MoodDtos.MoodRequest;
import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.CoachMealRequest;
import com.jllado.weightcontrol.api.dto.HabitDtos.HabitRequest;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest;
import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
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
    private final BloodPressureService bloodPressureService;
    private final LipidPanelService lipidPanelService;
    private final MoodService moodService;
    private final SleepService sleepService;
    private final MealService mealService;
    private final HabitService habitService;
    private final RoutineService routineService;
    private final DecisionOutcomeService decisionOutcomeService;

    public PersonalRecordMutationService(
        PersonalRecordService personalRecordService,
        WeightService weightService,
        WorkoutService workoutService,
        DashboardService dashboardService,
        BloodPressureService bloodPressureService,
        LipidPanelService lipidPanelService,
        MoodService moodService,
        SleepService sleepService,
        MealService mealService,
        HabitService habitService,
        RoutineService routineService,
        DecisionOutcomeService decisionOutcomeService
    ) {
        this.personalRecordService = personalRecordService;
        this.weightService = weightService;
        this.workoutService = workoutService;
        this.dashboardService = dashboardService;
        this.bloodPressureService = bloodPressureService;
        this.lipidPanelService = lipidPanelService;
        this.moodService = moodService;
        this.sleepService = sleepService;
        this.mealService = mealService;
        this.habitService = habitService;
        this.routineService = routineService;
        this.decisionOutcomeService = decisionOutcomeService;
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

    public MutationResult<BloodPressure> createBloodPressure(User user, BloodPressureRequest request) {
        var previous = personalRecordService.captureCurrentValues(user);
        BloodPressure result = bloodPressureService.create(user, request);
        dashboardService.refreshCurrentStatus(user);
        return achieved(user, previous, PersonalRecordSourceType.BLOOD_PRESSURE, result.getId(), result, result.getMeasuredAt().toLocalDate(), false);
    }

    public MutationResult<BloodPressure> updateBloodPressure(User user, Long id, BloodPressureRequest request) {
        BloodPressure result = bloodPressureService.update(user, id, request);
        dashboardService.refreshCurrentStatus(user);
        personalRecordService.rebuild(user);
        return unchanged(result);
    }

    public void deleteBloodPressure(User user, Long id) {
        bloodPressureService.delete(user, id);
        dashboardService.refreshCurrentStatus(user);
        personalRecordService.rebuild(user);
    }

    public MutationResult<LipidPanel> createLipidPanel(User user, LipidPanelRequest request) {
        var previous = personalRecordService.captureCurrentValues(user);
        LipidPanel result = lipidPanelService.create(user, request);
        return achieved(user, previous, PersonalRecordSourceType.LIPID_PANEL, result.getId(), result, result.getPanelDate(), false);
    }

    public MutationResult<LipidPanel> updateLipidPanel(User user, Long id, LipidPanelRequest request) {
        LipidPanel result = lipidPanelService.update(user, id, request);
        personalRecordService.rebuild(user);
        return unchanged(result);
    }

    public void deleteLipidPanel(User user, Long id) {
        lipidPanelService.delete(user, id);
        personalRecordService.rebuild(user);
    }

    public MutationResult<Mood> createMood(User user, MoodRequest request) {
        var previous = personalRecordService.captureCurrentValues(user);
        Mood result = moodService.create(user, request);
        return achieved(user, previous, PersonalRecordSourceType.MOOD, result.getId(), result, result.getMoodDate(), false);
    }

    public MutationResult<Mood> updateMood(User user, Long id, MoodRequest request) {
        Mood result = moodService.update(user, id, request);
        personalRecordService.rebuild(user);
        return unchanged(result);
    }

    public void deleteMood(User user, Long id) {
        moodService.delete(user, id);
        personalRecordService.rebuild(user);
    }

    public MutationResult<Sleep> createSleep(User user, SleepRequest request) {
        var previous = personalRecordService.captureCurrentValues(user);
        Sleep result = sleepService.create(user, request);
        return achieved(user, previous, PersonalRecordSourceType.SLEEP, result.getId(), result, result.getSleepDate(), false);
    }

    public MutationResult<Sleep> updateSleep(User user, Long id, SleepRequest request) {
        Sleep result = sleepService.update(user, id, request);
        personalRecordService.rebuild(user);
        return unchanged(result);
    }

    public void deleteSleep(User user, Long id) {
        sleepService.delete(user, id);
        personalRecordService.rebuild(user);
    }

    public MutationResult<Meal> createMeal(User user, MealRequest request) {
        var previous = personalRecordService.captureCurrentValues(user);
        Meal result = mealService.create(user, request);
        return achieved(user, previous, PersonalRecordSourceType.MEAL, result.getId(), result, result.getMealDate(), true);
    }

    public MutationResult<Meal> updateMeal(User user, Long id, MealRequest request) {
        Meal result = mealService.update(user, id, request);
        personalRecordService.rebuild(user);
        return unchanged(result);
    }

    public void deleteMeal(User user, Long id) {
        mealService.delete(user, id);
        personalRecordService.rebuild(user);
    }

    public Meal createConfirmedMeal(User user, CoachMealRequest request) {
        Meal result = mealService.createConfirmed(user, request);
        personalRecordService.rebuild(user);
        return result;
    }

    public Meal updateConfirmedMeal(User user, Long id, CoachMealRequest request) {
        Meal result = mealService.updateConfirmed(user, id, request);
        personalRecordService.rebuild(user);
        return result;
    }

    public void deleteConfirmedMeal(User user, Long id, boolean confirmed) {
        mealService.deleteConfirmed(user, id, confirmed);
        personalRecordService.rebuild(user);
    }

    public MutationResult<Habit> completeHabit(User user, Long id, java.time.LocalDate date) {
        var previous = personalRecordService.captureCurrentValues(user);
        Habit result = habitService.complete(user, id, date);
        return new MutationResult<>(result, personalRecordService.rebuildAndFindBehaviorAchievements(user, previous, "HABIT", id));
    }

    public Habit undoHabitCompletion(User user, Long id, java.time.LocalDate date) {
        Habit result = habitService.undoCompletion(user, id, date);
        personalRecordService.rebuild(user);
        return result;
    }

    public Habit updateHabit(User user, Long id, HabitRequest request) {
        Habit result = habitService.update(user, id, request);
        personalRecordService.rebuild(user);
        return result;
    }

    public void deleteHabit(User user, Long id) {
        habitService.delete(user, id);
        personalRecordService.rebuild(user);
    }

    public MutationResult<Routine> checkinRoutine(User user, Long id, java.time.OffsetDateTime date) {
        var previous = personalRecordService.captureCurrentValues(user);
        Routine result = routineService.checkin(user, id, date);
        return new MutationResult<>(result, personalRecordService.rebuildAndFindBehaviorAchievements(user, previous, "ROUTINE", id));
    }

    public Routine undoRoutineCheckin(User user, Long id, java.time.OffsetDateTime date) {
        Routine result = routineService.undoCheckin(user, id, date);
        personalRecordService.rebuild(user);
        return result;
    }

    public Routine updateRoutine(User user, Long id, RoutineRequest request) {
        Routine result = routineService.update(user, id, request);
        personalRecordService.rebuild(user);
        return result;
    }

    public void deleteRoutine(User user, Long id) {
        routineService.delete(user, id);
        personalRecordService.rebuild(user);
    }

    public MutationResult<DecisionOutcome> createDecisionOutcome(User user, DecisionOutcomeRequest request) {
        var previous = personalRecordService.captureCurrentValues(user);
        DecisionOutcome result = decisionOutcomeService.create(user, request);
        return new MutationResult<>(result, personalRecordService.rebuildAndFindBehaviorAchievements(user, previous, "BEHAVIOR", null));
    }

    private <T> MutationResult<T> achieved(User user, java.util.Map<String, java.math.BigDecimal> previous, PersonalRecordSourceType type, Long sourceId, T result, java.time.LocalDate date, boolean includeNutritionDay) {
        var achievements = personalRecordService.rebuildAndFindAchievements(user, previous, type, sourceId, date, includeNutritionDay, true);
        return new MutationResult<>(result, achievements);
    }

    private <T> MutationResult<T> unchanged(T result) {
        return new MutationResult<>(result, List.of());
    }

    public record MutationResult<T>(T result, List<RecordAchievementResponse> achievements) {
    }
}
