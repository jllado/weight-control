package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodRequest;
import com.jllado.weightcontrol.domain.DecisionOutcome;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.PersonalRecordSourceType;
import com.jllado.weightcontrol.domain.User;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
    private FastingPeriodService fastingPeriodService;

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
        when(decisionOutcomeService.create(user, new DecisionOutcomeRequest(LocalDate.of(2026, 8, 26), DecisionOutcomeType.WIN, null))).thenReturn(decision);

        var result = service.createDecisionOutcome(user, new DecisionOutcomeRequest(LocalDate.of(2026, 8, 26), DecisionOutcomeType.WIN, null));

        assertEquals(decision, result.result());
        assertEquals(java.util.List.of(), result.achievements());
        verify(personalRecordService, never()).captureCurrentValues(user);
        verify(personalRecordService, never()).rebuildAndFindBehaviorAchievements(user, java.util.Map.of(), "BEHAVIOR", null);
        verify(inAppNotificationService, never()).recordPersonalRecords(user, java.util.List.of());
    }

    @Test
    void creatingFastingPeriodRebuildsRecordsAndPublishesAchievements() {
        User user = user(null);
        FastingPeriodRequest request = new FastingPeriodRequest(
            OffsetDateTime.parse("2026-08-20T20:00:00+02:00"),
            OffsetDateTime.parse("2026-08-21T12:00:00+02:00"),
            null
        );
        FastingPeriod period = new FastingPeriod();
        period.setId(7L);
        var achievement = org.mockito.Mockito.mock(com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordAchievementResponse.class);
        when(personalRecordService.captureCurrentValues(user)).thenReturn(Map.of());
        when(fastingPeriodService.create(user, request)).thenReturn(period);
        when(personalRecordService.rebuildAndFindAchievements(user, Map.of(), PersonalRecordSourceType.FASTING_PERIOD, 7L, true))
            .thenReturn(List.of(achievement));

        var result = service.createFastingPeriod(user, request);

        assertEquals(period, result.result());
        assertEquals(List.of(achievement), result.achievements());
        verify(inAppNotificationService).recordPersonalRecords(user, List.of(achievement));
    }

    @Test
    void updatingAndDeletingFastingPeriodsRebuildWithoutAchievements() {
        User user = user(null);
        FastingPeriodRequest request = new FastingPeriodRequest(
            OffsetDateTime.parse("2026-08-20T20:00:00+02:00"),
            OffsetDateTime.parse("2026-08-21T12:00:00+02:00"),
            null
        );
        FastingPeriod period = new FastingPeriod();
        period.setId(7L);
        when(fastingPeriodService.update(user, 7L, request)).thenReturn(period);

        var updated = service.updateFastingPeriod(user, 7L, request);
        service.deleteFastingPeriod(user, 7L);

        assertEquals(List.of(), updated.achievements());
        verify(personalRecordService, org.mockito.Mockito.times(2)).rebuild(user);
        verify(inAppNotificationService, never()).recordPersonalRecords(user, List.of());
    }

    private User user(LocalDate lastCompletedDashboardDate) {
        User user = new User();
        user.setLastCompletedDashboardDate(lastCompletedDashboardDate);
        return user;
    }
}
