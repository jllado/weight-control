package com.jllado.weightcontrol.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class OvernightHeartRateAnalysis {
    private static final int MIN_NIGHTS = 7;
    private static final BigDecimal RECENT_MARGIN = BigDecimal.valueOf(5);
    private static final BigDecimal SUSTAINED_MARGIN = BigDecimal.valueOf(3);

    public record Night(LocalDate date, BigDecimal bpm) {}
    public record Limit(LocalDate effectiveFrom, BigDecimal bpm) {}
    public record Evidence(String label, LocalDate from, LocalDate through, List<Night> readings, BigDecimal bpm,
                           BigDecimal referenceBpm, BigDecimal thresholdBpm, LocalDate referenceFrom, LocalDate referenceThrough) {}
    public record Reason(String code, String label, LocalDate since, List<Evidence> evidence) {}
    public record Fraction(int elevated, int assessable) {}

    public static final class Result {
        private final List<Reason> reasons;
        private final NavigableMap<LocalDate, Boolean> elevatedByDate;

        private Result(List<Reason> reasons, NavigableMap<LocalDate, Boolean> elevatedByDate) {
            this.reasons = reasons;
            this.elevatedByDate = elevatedByDate;
        }

        public List<Reason> reasons() { return reasons; }

        public Fraction fraction(LocalDate from, LocalDate through) {
            var assessed = elevatedByDate.subMap(from, true, through, true).values();
            return new Fraction((int) assessed.stream().filter(Boolean::booleanValue).count(), assessed.size());
        }
    }

    private OvernightHeartRateAnalysis() {}

    public static Result evaluate(List<Night> nights, List<Limit> limits, LocalDate asOf) {
        var valid = nights.stream().filter(n -> !n.date().isAfter(asOf) && n.bpm() != null && n.bpm().signum() > 0)
            .sorted(Comparator.comparing(Night::date)).toList();
        var datedLimits = new TreeMap<LocalDate, BigDecimal>();
        limits.forEach(limit -> datedLimits.put(limit.effectiveFrom(), limit.bpm()));
        var elevatedByDate = new TreeMap<LocalDate, Boolean>();
        var recent = new Streak("RECENT_ELEVATION", "Recent elevation");
        var personal = new Streak("PERSONAL_LIMIT", "Above personal alert threshold");

        for (int i = 0; i < valid.size(); i++) {
            Night night = valid.get(i);
            Evidence baseline = recentEvidence(night, valid.subList(Math.max(0, i - 30), i));
            var limit = datedLimits.floorEntry(night.date());
            Evidence fixed = limit == null ? null : new Evidence("Personal alert threshold", night.date(), night.date(),
                List.of(night), night.bpm(), null, limit.getValue(), limit.getKey(), null);
            boolean aboveRecent = recent.observe(baseline);
            boolean abovePersonal = personal.observe(fixed);
            if (baseline != null || fixed != null) elevatedByDate.put(night.date(), aboveRecent || abovePersonal);
        }

        return new Result(Stream.of(recent.active, personal.active, sustainedRise(valid, asOf))
            .filter(Objects::nonNull).toList(), elevatedByDate);
    }

    private static Evidence recentEvidence(Night night, List<Night> prior) {
        if (prior.size() < MIN_NIGHTS) return null;
        BigDecimal reference = median(prior);
        return new Evidence("Prior-night median", night.date(), night.date(), List.of(night), night.bpm(),
            reference, reference.add(RECENT_MARGIN), prior.getFirst().date(), prior.getLast().date());
    }

    public static BigDecimal median(List<Night> nights) {
        var sorted = nights.stream().map(Night::bpm).sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) return sorted.get(middle);
        return sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.TWO);
    }

    private static final class Streak {
        private final String code;
        private final String label;
        private Evidence previous;
        private boolean previouslyAbove;
        private Reason active;

        private Streak(String code, String label) {
            this.code = code;
            this.label = label;
        }

        private boolean observe(Evidence current) {
            boolean above = current != null && current.bpm().compareTo(current.thresholdBpm()) > 0;
            if (previous != null && current != null && previous.from().plusDays(1).equals(current.from())) {
                if (active == null && above && previouslyAbove)
                    active = new Reason(code, label, current.from(), List.of(previous, current));
                else if (!above && !previouslyAbove) active = null;
            }
            previous = current;
            previouslyAbove = above;
            return above;
        }
    }

    private record Window(LocalDate from, LocalDate through, List<Night> readings, BigDecimal sum) {
        private static Window ending(NavigableMap<LocalDate, Night> nights, LocalDate through, int days) {
            LocalDate from = through.minusDays(days - 1);
            var readings = nights.subMap(from, true, through, true).values();
            if (readings.size() < MIN_NIGHTS) return null;
            return new Window(from, through, List.copyOf(readings),
                readings.stream().map(Night::bpm).reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        private BigDecimal count() { return BigDecimal.valueOf(readings.size()); }

        private BigDecimal displayMean() { return sum.divide(count(), 3, RoundingMode.HALF_UP).stripTrailingZeros(); }

        private boolean roseFrom(Window reference) {
            // Compare the means before rounding by multiplying both sides by both sample sizes.
            BigDecimal thresholdSum = reference.sum.add(SUSTAINED_MARGIN.multiply(reference.count()));
            return sum.multiply(reference.count()).compareTo(thresholdSum.multiply(count())) >= 0;
        }

        private Evidence evidence(String label, Window reference) {
            BigDecimal mean = reference == null ? null : reference.displayMean();
            return new Evidence(label, from, through, readings, displayMean(), mean,
                mean == null ? null : mean.add(SUSTAINED_MARGIN),
                reference == null ? null : reference.from, reference == null ? null : reference.through);
        }
    }

    private static Reason sustainedRise(List<Night> valid, LocalDate asOf) {
        if (valid.size() < 3 * MIN_NIGHTS) return null;
        var nights = new TreeMap<LocalDate, Night>();
        valid.forEach(n -> nights.put(n.date(), n));
        Window onsetReference = null;
        Reason active = null;
        // After the last recorded night leaves the latest fortnight, no further window can qualify.
        LocalDate lastPossible = valid.getLast().date().plusDays(13);
        for (LocalDate date = valid.getFirst().date(); !date.isAfter(asOf) && !date.isAfter(lastPossible); date = date.plusDays(1)) {
            Window second = Window.ending(nights, date, 14);
            if (second == null) continue;
            if (active != null) {
                if (second.from.isAfter(active.since()) && !second.roseFrom(onsetReference)) {
                    active = null;
                    onsetReference = null;
                }
                continue;
            }

            Window first = Window.ending(nights, date.minusDays(14), 14);
            if (first == null) continue;
            Window reference = Window.ending(nights, date.minusDays(28), 30);
            if (reference != null && first.roseFrom(reference) && second.roseFrom(reference)) {
                onsetReference = reference;
                active = new Reason("SUSTAINED_RISE", "Sustained rise", date, List.of(
                    reference.evidence("30-day reference mean", null),
                    first.evidence("First 14-day mean", reference),
                    second.evidence("Second 14-day mean", reference)));
            }
        }
        return active;
    }
}
