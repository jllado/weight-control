package com.jllado.weightcontrol.util;

import com.jllado.weightcontrol.domain.RoutineCadence;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Routine opportunities use Madrid dates; evidence always retains its actual date. */
public final class RoutinePeriods {
    private RoutinePeriods() { }

    public static LocalDate start(RoutineCadence cadence, LocalDate date) {
        return cadence == RoutineCadence.WEEKLY ? DateTimes.startOfDashboardWeek(date) : date;
    }

    public static LocalDate end(RoutineCadence cadence, LocalDate date) {
        return nextStart(cadence, date).minusDays(1);
    }

    public static LocalDate nextStart(RoutineCadence cadence, LocalDate date) {
        return start(cadence, date).plusDays(cadence == RoutineCadence.WEEKLY ? 7 : 1);
    }

    public static boolean contains(RoutineCadence cadence, LocalDate opportunityDate, LocalDate evidenceDate, LocalDate cutoff) {
        return !evidenceDate.isAfter(cutoff) && start(cadence, opportunityDate).equals(start(cadence, evidenceDate));
    }

    public static List<LocalDate> completedPeriods(RoutineCadence cadence, List<OffsetDateTime> evidence, LocalDate cutoff) {
        return evidence.stream().map(DateTimes::toLocalDate).filter(date -> !date.isAfter(cutoff))
            .map(date -> start(cadence, date)).distinct().sorted().toList();
    }
}
