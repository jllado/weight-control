package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.repository.DailyStatusRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeightPerformanceWeekTest {

    @Mock
    private WeightRepository weightRepository;
    @Mock
    private DailyStatusRepository dailyStatusRepository;
    @Mock
    private PhotoStorageService photoStorageService;
    @InjectMocks
    private WeightService service;

    @Test
    void linksFridayWeightToTheWeekEndingThatDay() {
        User user = new User();
        Weight weight = weight(user, LocalDate.of(2026, 8, 14));
        when(dailyStatusRepository.findByUserAndStatusDateBetweenOrderByStatusDateAsc(user, LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 14)))
            .thenReturn(List.of(status(8, 10), status(7, 10)));

        WeightPerformanceWeek week = service.getPerformanceWeek(weight);

        assertEquals(LocalDate.of(2026, 8, 8), week.startDate());
        assertEquals(LocalDate.of(2026, 8, 14), week.endDate());
        assertEquals(0, week.routineCompletionPercentage().compareTo(new java.math.BigDecimal("75.00")));
    }

    @Test
    void linksWeekendWeightToThePreviousCompletedWeek() {
        User user = new User();
        Weight weight = weight(user, LocalDate.of(2026, 8, 16));
        when(dailyStatusRepository.findByUserAndStatusDateBetweenOrderByStatusDateAsc(user, LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 14)))
            .thenReturn(List.of(status(1, 1)));

        WeightPerformanceWeek week = service.getPerformanceWeek(weight);

        assertEquals(LocalDate.of(2026, 8, 8), week.startDate());
        assertEquals(LocalDate.of(2026, 8, 14), week.endDate());
    }

    @Test
    void doesNotLinkMidweekWeight() {
        assertNull(service.getPerformanceWeek(weight(new User(), LocalDate.of(2026, 8, 12))));
    }

    private Weight weight(User user, LocalDate date) {
        Weight weight = new Weight();
        weight.setUser(user);
        weight.setMeasuredAt(DateTimes.startOfDay(date));
        return weight;
    }

    private DailyStatus status(int completed, int opportunities) {
        DailyStatus status = new DailyStatus();
        status.setRoutinesDone(completed);
        status.setTotalRoutines(opportunities);
        return status;
    }
}
