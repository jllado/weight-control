package com.jllado.weightcontrol.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateTimesTest {

    @Test
    void dashboardWeekStartsOnSaturday() {
        LocalDate saturday = LocalDate.of(2026, 7, 18);

        assertEquals(saturday, DateTimes.startOfDashboardWeek(saturday));
        assertEquals(saturday, DateTimes.startOfDashboardWeek(saturday.plusDays(1)));
        assertEquals(saturday, DateTimes.startOfDashboardWeek(saturday.plusDays(6)));
    }
}
