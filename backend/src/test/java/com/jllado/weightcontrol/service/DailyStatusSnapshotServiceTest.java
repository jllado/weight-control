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
    void rebuildPreservesManualRoutinesCompletion() {
        User user = new User();
        user.setId(1L);

        LocalDate date = LocalDate.of(2026, 6, 10);
        DailyStatus existing = new DailyStatus();
        existing.setRoutinesCompleted(false);

        when(dailyStatusRepository.findByUserAndStatusDate(user, date)).thenReturn(Optional.of(existing));
        when(dailyStatusRepository.save(any(DailyStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(weightRepository.findFirstByUserAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any())).thenReturn(Optional.empty());
        when(bloodPressureRepository.findFirstByUserAndMeasuredAtLessThanEqualOrderByMeasuredAtDesc(any(), any())).thenReturn(Optional.empty());
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of());

        DailyStatus status = service.rebuild(user, date);

        assertEquals(false, status.getRoutinesCompleted());
    }
}
