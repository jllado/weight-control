package com.jllado.weightcontrol.util;

import com.jllado.weightcontrol.domain.RoutineCadence;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.service.PlanningWeekService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Routine opportunities use Madrid dates; evidence always retains its actual date. */
public final class RoutinePeriods {
    private RoutinePeriods() { }

    public static LocalDate start(User user, RoutineCadence cadence, LocalDate date) {
        return cadence == RoutineCadence.WEEKLY ? PlanningWeekService.period(user, date).start() : date;
    }

    public static LocalDate end(User user, RoutineCadence cadence, LocalDate date) {
        return cadence == RoutineCadence.WEEKLY ? PlanningWeekService.period(user, date).end() : date;
    }

    public static LocalDate nextStart(User user, RoutineCadence cadence, LocalDate date) {
        return end(user, cadence, date).plusDays(1);
    }

    public static boolean contains(User user, RoutineCadence cadence, LocalDate opportunityDate, LocalDate evidenceDate, LocalDate cutoff) {
        return !evidenceDate.isAfter(cutoff) && start(user, cadence, opportunityDate).equals(start(user, cadence, evidenceDate));
    }

    public static List<LocalDate> completedPeriods(User user, RoutineCadence cadence, List<OffsetDateTime> evidence, LocalDate cutoff) {
        return evidence.stream().map(DateTimes::toLocalDate).filter(date -> !date.isAfter(cutoff))
            .map(date -> start(user, cadence, date)).distinct().sorted().toList();
    }
}
