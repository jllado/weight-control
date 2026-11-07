package com.jllado.weightcontrol.util;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.domain.RoutineCadence;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutinePeriodsTest {
    @Test void saturdayStartsAndFridayEndsTheOpportunityAcrossMadridClockChanges() {
        for (LocalDate saturday : List.of(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 10, 24))) {
            assertEquals(saturday, RoutinePeriods.start(RoutineCadence.WEEKLY, saturday.plusDays(6)));
            assertEquals(saturday.plusDays(6), RoutinePeriods.end(RoutineCadence.WEEKLY, saturday));
            assertEquals(saturday.plusWeeks(1), RoutinePeriods.nextStart(RoutineCadence.WEEKLY, saturday.plusDays(6)));
        }
        assertEquals(167, Duration.between(DateTimes.startOfDay(LocalDate.of(2026,3,28)), DateTimes.startOfDay(LocalDate.of(2026,4,4))).toHours());
        assertEquals(169, Duration.between(DateTimes.startOfDay(LocalDate.of(2026,10,24)), DateTimes.startOfDay(LocalDate.of(2026,10,31))).toHours());
    }

    @Test void historyUsesActualMadridEvidenceDatesBeforeDeduplicatingWeeks() {
        var evidence = List.of(OffsetDateTime.parse("2026-10-24T22:30Z"), OffsetDateTime.parse("2026-10-30T08:00+01:00"), OffsetDateTime.parse("2026-10-31T00:00+01:00"));
        assertTrue(RoutinePeriods.completedPeriods(RoutineCadence.WEEKLY, evidence, LocalDate.of(2026,10,24)).isEmpty());
        assertEquals(List.of(LocalDate.of(2026,10,24)), RoutinePeriods.completedPeriods(RoutineCadence.WEEKLY, evidence, LocalDate.of(2026,10,30)));
        assertEquals(List.of(LocalDate.of(2026,10,24), LocalDate.of(2026,10,31)), RoutinePeriods.completedPeriods(RoutineCadence.WEEKLY, evidence, LocalDate.of(2026,10,31)));
        assertFalse(RoutinePeriods.contains(RoutineCadence.WEEKLY, LocalDate.of(2026,10,24), LocalDate.of(2026,10,25), LocalDate.of(2026,10,24)));
        assertEquals(LocalDate.of(2026,10,25), RoutinePeriods.start(RoutineCadence.DAILY, LocalDate.of(2026,10,25)));
    }
}
