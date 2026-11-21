package com.jllado.weightcontrol.service;

import static com.jllado.weightcontrol.service.OvernightHeartRateAnalysis.evaluate;
import static org.assertj.core.api.Assertions.assertThat;

import com.jllado.weightcontrol.service.OvernightHeartRateAnalysis.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class OvernightHeartRateAnalysisTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    void excludesUnassessableNightsUntilSevenPriorReadingsAndComparesEachNightToItsOwnMedian() {
        List<Night> nights = new ArrayList<>(List.of(night(0, "60"), night(1, "60"), night(2, "60"),
            night(3, "60"), night(4, "62"), night(5, "62"), night(6, "62"), night(7, "65.01"), night(8, "66.00")));
        var equal = evaluate(nights, List.of(), date(8));
        assertThat(equal.reasons()).isEmpty();
        assertThat(equal.fraction(date(0), date(8))).isEqualTo(new Fraction(1, 2));

        nights.set(8, night(8, "66.01"));
        Collections.reverse(nights);
        var reason = evaluate(nights, List.of(), date(8)).reasons().getFirst();
        assertThat(reason.code()).isEqualTo("RECENT_ELEVATION");
        assertThat(reason.since()).isEqualTo(date(8));
        assertThat(reason.evidence()).extracting(Evidence::thresholdBpm)
            .usingElementComparator(BigDecimal::compareTo).containsExactly(decimal("65"), decimal("66"));
        assertThat(reason.evidence().getLast().readings()).containsExactly(night(8, "66.01"));
    }

    @Test
    void usesLatestThirtyValidNightsEvenAcrossCalendarGaps() {
        List<Night> nights = series(0, 19, "90");
        nights.addAll(series(100, 129, "60"));
        nights.add(night(160, "65.1"));
        nights.add(night(161, "65.1"));
        var reason = evaluate(nights, List.of(), date(161)).reasons().getFirst();
        assertThat(reason.code()).isEqualTo("RECENT_ELEVATION");
        assertThat(reason.evidence().getFirst().referenceFrom()).isEqualTo(date(100));
        assertThat(reason.evidence().getLast().referenceFrom()).isEqualTo(date(101));
        assertThat(reason.evidence().getLast().referenceThrough()).isEqualTo(date(160));
    }

    @Test
    void isolatesFutureReadingsAndMissingOrZeroNightsBreakBothOpeningAndRecovery() {
        List<Night> nights = series(0, 6, "60");
        nights.addAll(List.of(night(7, "66"), new Night(date(8), null), night(9, "66"),
            night(10, "0"), night(11, "66"), night(12, "66"), night(13, "65"),
            night(15, "65"), night(16, "65")));
        var limits = List.of(new Limit(date(0), decimal("65")));
        var before = evaluate(nights, limits, date(11));
        assertThat(before.reasons()).isEmpty();
        assertThat(before.fraction(date(7), date(50))).isEqualTo(new Fraction(3, 3));
        var active = evaluate(nights, limits, date(15));
        assertThat(active.reasons()).extracting(Reason::code).containsExactly("RECENT_ELEVATION", "PERSONAL_LIMIT");
        assertThat(evaluate(nights, limits, date(16)).reasons()).isEmpty();
    }

    @Test
    void fixedLimitsChangeOnTheirEffectiveDatesAndOverlapCountsOnce() {
        List<Night> nights = series(0, 6, "60");
        nights.addAll(List.of(night(7, "66"), night(8, "68"), night(9, "68"), night(10, "68")));
        var limits = List.of(new Limit(date(9), decimal("68")), new Limit(date(7), decimal("65.5")));
        var opening = evaluate(nights, limits, date(8));
        assertThat(opening.reasons()).extracting(Reason::code).containsExactly("RECENT_ELEVATION", "PERSONAL_LIMIT");
        assertThat(opening.reasons().getLast().evidence().getLast().referenceFrom()).isEqualTo(date(7));
        var after = evaluate(nights, limits, date(10));
        assertThat(after.reasons()).extracting(Reason::code).containsExactly("RECENT_ELEVATION");
        assertThat(after.fraction(date(0), date(10))).isEqualTo(new Fraction(4, 4));
    }

    @Test
    void editsAndDeletesRebuildOnsetAndIsolatedBreachesStillCount() {
        var nights = series(0, 8, "70");
        var limits = List.of(new Limit(date(0), decimal("70.5")));
        nights.set(7, night(7, "71"));
        nights.set(8, night(8, "71"));
        assertThat(evaluate(nights, limits, date(8)).reasons()).extracting(Reason::code).containsExactly("PERSONAL_LIMIT");
        nights.set(7, night(7, "70.5"));
        assertThat(evaluate(nights, limits, date(8)).reasons()).isEmpty();
        nights.remove(7);
        var deleted = evaluate(nights, limits, date(8));
        assertThat(deleted.reasons()).isEmpty();
        assertThat(deleted.fraction(date(0), date(8))).isEqualTo(new Fraction(1, 8));
    }

    @Test
    void sustainedRiseRequiresTwoFortnightsAndIncludesTheExactThreeBpmBoundary() {
        var nights = series(0, 29, "60");
        nights.addAll(series(30, 57, "63"));
        assertThat(evaluate(nights, List.of(), date(43)).reasons()).isEmpty();
        assertThat(evaluate(nights, List.of(), date(56)).reasons()).isEmpty();
        var reason = evaluate(nights, List.of(), date(57)).reasons().getFirst();
        assertThat(reason.code()).isEqualTo("SUSTAINED_RISE");
        assertThat(reason.since()).isEqualTo(date(57));
        assertThat(reason.evidence()).extracting(Evidence::from).containsExactly(date(0), date(30), date(44));
        assertThat(reason.evidence().get(1).readings()).hasSize(14);
        nights.set(45, night(45, "62.999"));
        assertThat(evaluate(nights, List.of(), date(57)).reasons()).isEmpty();
    }

    @Test
    void eachWindowNeedsSevenValidReadingsAndCalendarGapsMayFallInsideAWindow() {
        var nights = series(23, 29, "60");
        nights.addAll(series(37, 43, "63"));
        nights.addAll(series(51, 57, "63"));
        assertThat(evaluate(nights, List.of(), date(57)).reasons()).extracting(Reason::code).containsExactly("SUSTAINED_RISE");
        for (int removed : List.of(23, 37, 51)) {
            var fewer = nights.stream().filter(night -> !night.date().equals(date(removed))).toList();
            assertThat(evaluate(fewer, List.of(), date(57)).reasons()).isEmpty();
        }
    }

    @Test
    void slowRiseTriggersWithoutIsolatedSpikesAndRecoveryUsesOnsetReference() {
        var nights = series(0, 29, "60");
        nights.addAll(series(30, 36, "62"));
        nights.addAll(series(37, 43, "64"));
        nights.addAll(series(44, 57, "65"));
        nights.addAll(series(58, 71, "63"));
        nights.add(night(72, "62.99"));
        var stillActive = evaluate(nights, List.of(), date(71));
        assertThat(stillActive.reasons()).extracting(Reason::code).containsExactly("SUSTAINED_RISE");
        assertThat(stillActive.reasons().getFirst().evidence().getFirst().bpm()).isEqualByComparingTo("60");
        assertThat(stillActive.fraction(date(0), date(71)).elevated()).isZero();
        assertThat(evaluate(nights, List.of(), date(72)).reasons()).isEmpty();
    }

    @Test
    void sustainedAndPersonalReasonsRecoverIndependently() {
        var nights = series(0, 29, "60");
        nights.addAll(series(30, 57, "63"));
        var limits = List.of(new Limit(date(0), decimal("62")), new Limit(date(58), decimal("64")));
        nights.addAll(series(58, 71, "63"));
        assertThat(evaluate(nights, limits, date(58)).reasons()).extracting(Reason::code)
            .containsExactly("PERSONAL_LIMIT", "SUSTAINED_RISE");
        assertThat(evaluate(nights, limits, date(59)).reasons()).extracting(Reason::code)
            .containsExactly("SUSTAINED_RISE");
        assertThat(evaluate(nights, limits, date(71)).reasons()).extracting(Reason::code)
            .containsExactly("SUSTAINED_RISE");
    }

    @Test
    void recoveryNeedsACompleteSubsequentFortnightAndSevenNights() {
        var nights = series(0, 29, "60");
        nights.addAll(series(30, 57, "63"));
        nights.addAll(series(58, 63, "60"));
        assertThat(evaluate(nights, List.of(), date(70)).reasons()).hasSize(1);
        assertThat(evaluate(nights, List.of(), date(71)).reasons()).hasSize(1);
        nights.add(night(64, "60"));
        assertThat(evaluate(nights, List.of(), date(70)).reasons()).hasSize(1);
        assertThat(evaluate(nights, List.of(), date(71)).reasons()).isEmpty();
    }

    private static List<Night> series(int first, int last, String bpm) {
        var nights = new ArrayList<Night>();
        for (int i = first; i <= last; i++) nights.add(night(i, bpm));
        return nights;
    }

    private static Night night(int day, String bpm) { return new Night(date(day), decimal(bpm)); }
    private static LocalDate date(int day) { return START.plusDays(day); }
    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
}
