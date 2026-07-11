package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyStatusSnapshotServiceTest {

    @Mock
    private DailyStatusRepository dailyStatusRepository;

    @Mock
    private WeightRepository weightRepository;

    @Mock
    private BloodPressureRepository bloodPressureRepository;

    @Mock
    private RoutineRepository routineRepository;

    @Mock
    private RoutineCheckinRepository routineCheckinRepository;

    @InjectMocks
    private DailyStatusSnapshotService service;

    @Test
    void rebuildSkipsActiveDateForRoutinesTrendStatus() {
        User user = new User();
        user.setId(1L);

        LocalDate date = LocalDate.of(2026, 6, 10);
        Routine routine = new Routine();
        routine.setId(1L);
        routine.setUser(user);
        routine.setStartDate(DateTimes.startOfDay(LocalDate.of(2026, 6, 1)));

        when(dailyStatusRepository.findByUserAndStatusDate(user, date)).thenReturn(Optional.empty());
        when(dailyStatusRepository.save(any(DailyStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(weightRepository.findFirstByUserAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any())).thenReturn(Optional.empty());
        when(bloodPressureRepository.findFirstByUserAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any())).thenReturn(Optional.empty());
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of(routine));
        when(routineCheckinRepository.findByRoutineOrderByCheckedAtAsc(routine)).thenReturn(List.of());
        when(routineCheckinRepository.countByRoutineAndCheckedAtBetween(routine, DateTimes.startOfDay(date.minusDays(1)).minusDays(31), DateTimes.startOfDay(date.minusDays(1)).plusDays(1)))
            .thenReturn(9L);

        DailyStatus status = service.rebuild(user, date);

        assertEquals(0, new BigDecimal("100.00").compareTo(status.getRoutinesStatus()));
        assertEquals(0, new BigDecimal("1.00").compareTo(status.getRoutinesScore()));
    }

    @Test
    void getFullWeekReturnsSaturdayThroughFridayForAnchorWeek() {
        User user = new User();
        user.setId(1L);

        LocalDate saturday = LocalDate.of(2026, 6, 13);
        for (int index = 0; index < 7; index++) {
            LocalDate date = saturday.plusDays(index);
            when(dailyStatusRepository.findByUserAndStatusDate(user, date)).thenReturn(Optional.of(status(date)));
        }

        List<DailyStatus> week = service.getFullWeek(user, LocalDate.of(2026, 6, 13));

        assertEquals(7, week.size());
        assertEquals(saturday, week.getFirst().getStatusDate());
        assertEquals(LocalDate.of(2026, 6, 19), week.getLast().getStatusDate());
    }

    @Test
    void getWeekStopsAtAnchorDate() {
        User user = new User();
        user.setId(1L);

        LocalDate saturday = LocalDate.of(2026, 6, 13);
        when(dailyStatusRepository.findByUserAndStatusDate(user, saturday)).thenReturn(Optional.of(status(saturday)));

        List<DailyStatus> week = service.getWeek(user, saturday);

        assertEquals(1, week.size());
        assertEquals(saturday, week.getFirst().getStatusDate());
    }

    private DailyStatus status(LocalDate date) {
        DailyStatus status = new DailyStatus();
        status.setStatusDate(date);
        return status;
    }
}
