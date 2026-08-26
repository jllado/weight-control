package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalRecordMutationServiceTest {

    @Mock
    private PersonalRecordService personalRecordService;

    @Mock
    private WeightService weightService;

    @Mock
    private WorkoutService workoutService;

    @Mock
    private DashboardService dashboardService;

    @Mock
    private BloodPressureService bloodPressureService;

    @Mock
    private LipidPanelService lipidPanelService;

    @Mock
    private MoodService moodService;

    @Mock
    private SleepService sleepService;

    @Mock
    private MealService mealService;

    @Mock
    private HabitService habitService;

    @Mock
    private RoutineService routineService;

    @Mock
    private DecisionOutcomeService decisionOutcomeService;

    @Mock
    private InAppNotificationService inAppNotificationService;

    @InjectMocks
    private PersonalRecordMutationService service;

    @Test
    void refreshRoutineDashboardSkipsRecordRebuildForAnIncompleteDay() {
        User user = user(LocalDate.of(2026, 8, 22));
        OffsetDateTime changedAt = OffsetDateTime.parse("2026-08-23T08:00:00+02:00");

        service.refreshRoutineDashboard(user, changedAt);

        verify(dashboardService).refreshCurrentStatus(user);
        verify(personalRecordService, never()).rebuild(user);
    }

    @Test
    void refreshRoutineDashboardRebuildsRecordsForACompletedDay() {
        User user = user(LocalDate.of(2026, 8, 23));
        OffsetDateTime changedAt = OffsetDateTime.parse("2026-08-23T08:00:00+02:00");

        service.refreshRoutineDashboard(user, changedAt);

        verify(dashboardService).refreshCurrentStatus(user);
        verify(personalRecordService).rebuild(user);
    }

    @Test
    void creatingDecisionDoesNotCreatePersonalRecordAchievements() {
        User user = user(null);
        DecisionOutcome decision = new DecisionOutcome();
        when(decisionOutcomeService.create(user, new DecisionOutcomeRequest(LocalDate.of(2026, 8, 26), DecisionOutcomeType.WIN))).thenReturn(decision);

        var result = service.createDecisionOutcome(user, new DecisionOutcomeRequest(LocalDate.of(2026, 8, 26), DecisionOutcomeType.WIN));

        assertEquals(decision, result.result());
        assertEquals(java.util.List.of(), result.achievements());
        verify(personalRecordService, never()).captureCurrentValues(user);
        verify(personalRecordService, never()).rebuildAndFindBehaviorAchievements(user, java.util.Map.of(), "BEHAVIOR", null);
        verify(inAppNotificationService, never()).recordPersonalRecords(user, java.util.List.of());
    }

    private User user(LocalDate lastCompletedDashboardDate) {
        User user = new User();
        user.setLastCompletedDashboardDate(lastCompletedDashboardDate);
        return user;
    }
}
