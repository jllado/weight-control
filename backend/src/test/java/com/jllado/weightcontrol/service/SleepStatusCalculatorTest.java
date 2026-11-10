package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.Sleep;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SleepStatusCalculatorTest {
    private Sleep night(String start, String end, int seconds) {
        var sleep = new Sleep();
        sleep.setBedtimeStart(OffsetDateTime.parse(start));
        sleep.setBedtimeEnd(OffsetDateTime.parse(end));
        sleep.setSleepDate(sleep.getBedtimeEnd().toLocalDate());
        sleep.setTotalSleepDuration(seconds);
        return sleep;
    }

    @Test void consistentRecordedClocksRetainExcellentScoreAcrossTravel() {
        var sleeps = List.of(night("2026-10-10T23:00:00-05:00", "2026-10-11T07:00:00-05:00", 25200),
            night("2026-10-11T23:00:00+02:00", "2026-10-12T07:00:00+02:00", 25200));
        assertEquals(4, SleepStatusCalculator.score(sleeps));
    }

    @Test void midpointUsesBothLocalEndpointClocksAcrossDst() {
        var sleeps = List.of(night("2026-03-28T22:30:00+01:00", "2026-03-29T06:30:00+02:00", 23400),
            night("2026-10-25T00:00:00+02:00", "2026-10-25T08:00:00+01:00", 28800));
        assertEquals(4, SleepStatusCalculator.score(sleeps));
    }

    @Test void metDurationGoalsDoNotHideFailedEfficiency() {
        assertEquals(3, SleepStatusCalculator.score(List.of(night("2026-11-01T23:00:00Z", "2026-11-02T07:00:00Z", 23400))));
    }

    @Test void ratingRetainsInclusiveDurationAndEfficiencyAndStrictConsistencyBoundaries() {
        for (int[] c : new int[][]{{25199,21600,3},{25200,21600,4},{25200,21599,3},{28800,24480,4},{28800,24479,3}}) {
            var end = OffsetDateTime.parse("2026-11-02T07:00:00Z");
            assertEquals(c[2], SleepStatusCalculator.score(List.of(night(end.minusSeconds(c[0]).toString(), end.toString(), c[1]))));
        }
        assertEquals(3, SleepStatusCalculator.score(List.of(night("2026-11-01T22:00:00Z", "2026-11-02T06:00:00Z",25200),
            night("2026-11-02T00:00:00Z", "2026-11-02T08:00:00Z",25200))));
    }

    @Test void weeklyScoreStillRequiresSevenDistinctCompleteDates() {
        var nights = IntStream.range(0,7).mapToObj(i -> {
            var end = OffsetDateTime.parse("2026-11-02T07:00:00Z").plusDays(i);
            return night(end.minusHours(8).toString(), end.toString(), 25200);
        }).toList();
        assertNull(SleepStatusCalculator.weeklyScore(nights.subList(0,6)));
        assertEquals(4, SleepStatusCalculator.weeklyScore(nights));
        nights.get(6).setTotalSleepDuration(null);
        assertNull(SleepStatusCalculator.weeklyScore(nights));
    }
}
