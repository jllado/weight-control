package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.CoachDtos.RecoveryContext;
import com.jllado.weightcontrol.domain.Sleep;
import java.util.Arrays;
import java.util.List;

/** Established four-criterion score; weekly classification requires seven complete dated nights. */
public final class SleepStatusCalculator {
    private SleepStatusCalculator() { }
    static List<Sleep> completeSleeps(List<Sleep> sleeps) {
        return sleeps.stream().filter(s -> s.getBedtimeStart() != null && s.getBedtimeEnd() != null && s.getTotalSleepDuration() != null).toList();
    }
    static Integer weeklyScore(List<Sleep> sleeps) {
        var complete = completeSleeps(sleeps);
        return complete.stream().map(Sleep::getSleepDate).distinct().count() < 7 ? null : score(complete);
    }

    static int score(List<Sleep> sleeps) {
        double inBed = sleeps.stream().mapToLong(s -> java.time.Duration.between(s.getBedtimeStart(), s.getBedtimeEnd()).getSeconds()).average().orElse(0);
        double total = sleeps.stream().mapToInt(Sleep::getTotalSleepDuration).average().orElse(0);
        double efficiency = sleeps.stream().mapToDouble(s -> s.getTotalSleepDuration() * 100d / java.time.Duration.between(s.getBedtimeStart(), s.getBedtimeEnd()).getSeconds()).average().orElse(0);
        double[] midpointMinutes = sleeps.stream().mapToDouble(s -> {
            var start = s.getBedtimeStart().toLocalDateTime();
            var end = s.getBedtimeEnd().toLocalDateTime();
            // Each endpoint retains its own recorded offset, including travel and DST.
            double localMinutes = java.time.Duration.between(start, end).toNanos() / 60_000_000_000d;
            return ((start.toLocalTime().toNanoOfDay() / 60_000_000_000d + localMinutes / 2) % 1440 + 1440) % 1440;
        }).toArray();
        double meanSin = Arrays.stream(midpointMinutes).map(m -> Math.sin(m * 2 * Math.PI / 1440)).average().orElseThrow();
        double meanCos = Arrays.stream(midpointMinutes).map(m -> Math.cos(m * 2 * Math.PI / 1440)).average().orElseThrow();
        double midpoint = (Math.atan2(meanSin, meanCos) * 1440 / (2 * Math.PI) + 1440) % 1440;
        double deviation = Math.sqrt(Arrays.stream(midpointMinutes).map(m -> {
            double d = ((m - midpoint + 720) % 1440 + 1440) % 1440 - 720;
            return d * d;
        }).average().orElseThrow());
        return (inBed >= RecoveryContext.MINIMUM_TIME_IN_BED_SECONDS ? 1 : 0) + (total >= RecoveryContext.MINIMUM_TOTAL_SLEEP_SECONDS ? 1 : 0) + (efficiency >= 85 ? 1 : 0) + (deviation < 60 ? 1 : 0);
    }

}
