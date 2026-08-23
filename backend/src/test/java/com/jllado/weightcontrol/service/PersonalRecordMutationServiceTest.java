package com.jllado.weightcontrol.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    private User user(LocalDate lastCompletedDashboardDate) {
        User user = new User();
        user.setLastCompletedDashboardDate(lastCompletedDashboardDate);
        return user;
    }
}
