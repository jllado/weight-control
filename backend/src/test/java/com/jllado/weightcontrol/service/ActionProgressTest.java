package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActionProgressTest {
    private final LocalDate today = LocalDate.of(2026, 10, 25);

    @Test void todayPendingPreservesYesterdayStreakButMissedPastDayResets() {
        var checks = Set.of(today.minusDays(2), today.minusDays(1));
        assertEquals(2, progress(checks, today, null).streak());
        assertEquals(0, progress(Set.of(today.minusDays(2)), today, null).streak());
        assertEquals(1, progress(checks, today.minusDays(2), null).streak());
    }

    @Test void correctedAchievementKeepsLaterChecksAndCompletionDayAllowsUndo() {
        var checks = Set.of(today.minusDays(2), today.minusDays(1), today);
        var completed = progress(checks, today, null);
        assertEquals("COMPLETED", completed.status());
        assertEquals(today, completed.completedDate());
        assertEquals(100, completed.percentage());
        assertEquals("ACTIVE", progress(Set.of(today.minusDays(2), today), today, null).status());
        assertEquals("ACTIVE", progress(checks, today.minusDays(1), null).status());
        assertEquals(1, progress(Set.of(today.minusDays(2), today), today, null).streak());
    }

    @Test void explicitStopIsNotReopenedByCorrectionsAndScheduledDaysArePending() {
        assertEquals("STOPPED", progress(Set.of(today.minusDays(2), today.minusDays(1), today), today, today).status());
        assertEquals("SCHEDULED", ActionProgress.calculate(today.plusDays(1), 3, Set.of(), null, today, today).status());
    }

    @Test void calendarDatesSurviveMadridDstWithoutDurationArithmetic() {
        var checks = Set.of(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26));
        var result = ActionProgress.calculate(LocalDate.of(2026, 10, 24), 3, checks, null,
            LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 26));
        assertEquals(3, result.streak());
        assertEquals(3, result.bestStreak());
    }

    @Test void stoppingScheduledAttemptImmediatelyMovesItToHistory() {
        assertEquals("STOPPED", ActionProgress.calculate(today.plusDays(5), 14, Set.of(), today, today, today).status());
    }

    private ActionProgress progress(Set<LocalDate> checks, LocalDate date, LocalDate stopped) {
        return ActionProgress.calculate(today.minusDays(4), 3, checks, stopped, date, today);
    }
}
