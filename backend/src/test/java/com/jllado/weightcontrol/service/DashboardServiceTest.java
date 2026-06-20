package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.DashboardDtos;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DailyStatusSnapshotService snapshotService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MoodService moodService;

    @InjectMocks
    private DashboardService service;

    @Test
    void getDashboardIncludesMoodSummariesAndAverages() {
        User user = new User();
        user.setId(1L);
        user.setDashboardAnchorDate(LocalDate.of(2026, 6, 20));

        DailyStatus current = status(user, LocalDate.of(2026, 6, 20));
        DailyStatus lastWeek = status(user, LocalDate.of(2026, 6, 13));
        List<DailyStatus> week = List.of(
            status(user, LocalDate.of(2026, 6, 14)),
            status(user, LocalDate.of(2026, 6, 15)),
            status(user, LocalDate.of(2026, 6, 16)),
            status(user, LocalDate.of(2026, 6, 17)),
            status(user, LocalDate.of(2026, 6, 18)),
            status(user, LocalDate.of(2026, 6, 19)),
            current
        );
        List<DailyStatus> weekAgo = List.of(
            status(user, LocalDate.of(2026, 6, 7)),
            status(user, LocalDate.of(2026, 6, 8)),
            status(user, LocalDate.of(2026, 6, 9)),
            status(user, LocalDate.of(2026, 6, 10)),
            status(user, LocalDate.of(2026, 6, 11)),
            status(user, LocalDate.of(2026, 6, 12)),
            lastWeek
        );

        Map<LocalDate, Mood> moods = Map.of(
            LocalDate.of(2026, 6, 13), mood(2L, user, LocalDate.of(2026, 6, 13), 3),
            LocalDate.of(2026, 6, 14), mood(3L, user, LocalDate.of(2026, 6, 14), 2),
            LocalDate.of(2026, 6, 18), mood(4L, user, LocalDate.of(2026, 6, 18), 4),
            LocalDate.of(2026, 6, 20), mood(1L, user, LocalDate.of(2026, 6, 20), 5)
        );

        when(snapshotService.getOrBuild(user, user.getDashboardAnchorDate())).thenReturn(current);
        when(snapshotService.getLastWeekDailyStatus(user, user.getDashboardAnchorDate())).thenReturn(lastWeek);
        when(snapshotService.getWeek(user, user.getDashboardAnchorDate())).thenReturn(week);
        when(snapshotService.getWeek(user, user.getDashboardAnchorDate().minusDays(7))).thenReturn(weekAgo);
        when(moodService.findByDateRange(user, LocalDate.of(2026, 5, 14), LocalDate.of(2026, 6, 20))).thenReturn(moods);
        when(moodService.getAverage(user, LocalDate.of(2026, 6, 14), LocalDate.of(2026, 6, 20))).thenReturn(new BigDecimal("3.67"));
        when(moodService.getAverage(user, LocalDate.of(2026, 6, 7), LocalDate.of(2026, 6, 13))).thenReturn(new BigDecimal("3.00"));
        when(moodService.average(anyList())).thenCallRealMethod();

        DashboardDtos.DashboardResponse dashboard = service.getDashboard(user);

        assertEquals(5, dashboard.dailyStatus().mood().value());
        assertEquals(0, new BigDecimal("3.50").compareTo(dashboard.dailyStatus().moodTrend()));
        assertEquals(3, dashboard.lastWeekDailyStatus().mood().value());
        assertEquals(0, new BigDecimal("3.00").compareTo(dashboard.lastWeekDailyStatus().moodTrend()));
        assertEquals(0, new BigDecimal("3.67").compareTo(dashboard.weekStatus().moodAverage()));
        assertEquals(0, new BigDecimal("3.00").compareTo(dashboard.weekAgoStatus().moodAverage()));
    }

    private DailyStatus status(User user, LocalDate date) {
        DailyStatus status = new DailyStatus();
        status.setId(Long.valueOf(date.getDayOfMonth()));
        status.setUser(user);
        status.setStatusDate(date);
        status.setTotalRoutines(1);
        status.setTotalWeightRoutines(1);
        status.setTotalBloodPressureRoutines(1);
        status.setTotalFlexibilityRoutines(1);
        status.setTotalMindRoutines(1);
        status.setRoutinesDone(1);
        status.setWeightDone(1);
        status.setBloodPressureDone(1);
        status.setFlexibilityDone(1);
        status.setMindDone(1);
        status.setRoutinesPercentage(new BigDecimal("100.00"));
        status.setWeightPercentage(new BigDecimal("100.00"));
        status.setBloodPressurePercentage(new BigDecimal("100.00"));
        status.setFlexibilityPercentage(new BigDecimal("100.00"));
        status.setMindPercentage(new BigDecimal("100.00"));
        status.setRoutinesScore(new BigDecimal("1.00"));
        status.setWeightScore(new BigDecimal("1.00"));
        status.setBloodPressureScore(new BigDecimal("1.00"));
        status.setFlexibilityScore(new BigDecimal("1.00"));
        status.setMindScore(new BigDecimal("1.00"));
        status.setRoutinesStatus(new BigDecimal("100.00"));
        status.setWeightStatus(new BigDecimal("100.00"));
        status.setBloodPressureStatus(new BigDecimal("100.00"));
        status.setFlexibilityStatus(new BigDecimal("100.00"));
        status.setMindStatus(new BigDecimal("100.00"));
        return status;
    }

    private Mood mood(Long id, User user, LocalDate date, int value) {
        Mood mood = new Mood();
        mood.setId(id);
        mood.setUser(user);
        mood.setMoodDate(date);
        mood.setValue(value);
        return mood;
    }
}
