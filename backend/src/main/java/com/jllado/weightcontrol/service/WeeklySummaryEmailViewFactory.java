package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageSleep;
import com.jllado.weightcontrol.service.WeeklyMetrics.CalorieSummary;
import com.jllado.weightcontrol.service.WeeklyMetrics.DecisionMetrics;
import com.jllado.weightcontrol.service.WeeklyMetrics.Progress;
import com.jllado.weightcontrol.service.WeeklyMetrics.RoutineCompletion;
import com.jllado.weightcontrol.service.WeeklyMetrics.Summary;
import com.jllado.weightcontrol.service.WeeklyMetrics.WorkoutSummary;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.CardRow;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.Comparison;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.ComparisonStatus;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.DayView;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.MetricCard;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class WeeklySummaryEmailViewFactory {

    private static final DateTimeFormatter SUBJECT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter RANGE_END_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public WeeklySummaryEmailView create(User user, Progress progress, WeeklySummaryMeasurements measurements, String appUrl) {
        Summary current = progress.currentPeriod();
        Summary previous = progress.previousComparablePeriod();
        Summary yearAgo = progress.yearAgoComparablePeriod();
        RoutineCompletion routines = current.routineCompletion();
        String dateRange = current.startDate().format(SUBJECT_DATE) + " – " + current.endDate().format(RANGE_END_DATE);
        List<MetricCard> cards = List.of(
            calorieCard(current, previous, yearAgo),
            sleepCard(current, previous, yearAgo),
            moodCard(current, previous, yearAgo),
            weightCard(measurements),
            bloodPressureCard(measurements),
            workoutCard(current, previous, yearAgo),
            decisionCard(current, previous, yearAgo)
        );
        return new WeeklySummaryEmailView(
            "Your Weight Control weekly summary — " + current.startDate().format(SUBJECT_DATE) + "–" + current.endDate().format(SUBJECT_DATE),
            user.getDisplayName() == null ? user.getEmail() : user.getDisplayName(),
            dateRange,
            routines.percentage() == null ? "Not applicable" : percentage(routines.percentage()),
            routines.opportunities() == 0
                ? "No active routine opportunities"
                : routines.completed() + " of " + routines.opportunities() + " routine opportunities completed",
            routineComparison(routines, previous.routineCompletion(), "last week"),
            routineComparison(routines, yearAgo.routineCompletion(), "52 weeks ago"),
            current.routineCompletion().days().stream()
                .map(day -> new DayView(
                    day.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    day.percentage() == null ? "—" : percentage(day.percentage())
                ))
                .toList(),
            rows(cards),
            appUrl
        );
    }

    private MetricCard calorieCard(Summary current, Summary previous, Summary yearAgo) {
        CalorieSummary value = current.calories();
        return new MetricCard(
            "Average calories",
            value.averageCalories() == null ? "Not recorded" : whole(value.averageCalories()) + " kcal/day",
            dayCoverage(value.entryCount()),
            calorieComparison(value, previous.calories(), "last week"),
            calorieComparison(value, yearAgo.calories(), "52 weeks ago")
        );
    }

    private MetricCard sleepCard(Summary current, Summary previous, Summary yearAgo) {
        AverageSleep value = current.sleep();
        return new MetricCard(
            "Average sleep",
            value == null ? "Not recorded" : sleepDuration(value.totalSleepSeconds()),
            value == null ? "0 of 7 nights recorded" : value.nightCount() + " of 7 nights recorded",
            sleepComparison(value, previous.sleep(), "last week"),
            sleepComparison(value, yearAgo.sleep(), "52 weeks ago")
        );
    }

    private MetricCard moodCard(Summary current, Summary previous, Summary yearAgo) {
        return new MetricCard(
            "Average mood",
            current.moodAverage() == null ? "Not recorded" : decimal(current.moodAverage(), 1) + " / 5",
            dayCoverage(current.moodDayCount()),
            decimalComparison(current.moodAverage(), previous.moodAverage(), "points", "last week", previous.moodDayCount() + "/7 days"),
            decimalComparison(current.moodAverage(), yearAgo.moodAverage(), "points", "52 weeks ago", yearAgo.moodDayCount() + "/7 days")
        );
    }

    private MetricCard weightCard(WeeklySummaryMeasurements measurements) {
        Weight value = measurements.currentPeriod().weight();
        return new MetricCard(
            "Latest weight",
            value == null ? "Not recorded" : decimal(value.getWeight(), 1) + " kg",
            measurementDate(value == null ? null : value.getMeasuredAt()),
            weightComparison(value, measurements.previousComparablePeriod().weight(), "last week"),
            weightComparison(value, measurements.yearAgoComparablePeriod().weight(), "52 weeks ago")
        );
    }

    private MetricCard bloodPressureCard(WeeklySummaryMeasurements measurements) {
        BloodPressure value = measurements.currentPeriod().bloodPressure();
        return new MetricCard(
            "Latest blood pressure",
            value == null ? "Not recorded" : value.getUpper() + " / " + value.getLower() + " mmHg",
            measurementDate(value == null ? null : value.getMeasuredAt()),
            bloodPressureComparison(value, measurements.previousComparablePeriod().bloodPressure(), "last week"),
            bloodPressureComparison(value, measurements.yearAgoComparablePeriod().bloodPressure(), "52 weeks ago")
        );
    }

    private MetricCard workoutCard(Summary current, Summary previous, Summary yearAgo) {
        WorkoutSummary value = current.workouts();
        return new MetricCard(
            "Workouts",
            value.workoutCount() + (value.workoutCount() == 1 ? " session" : " sessions"),
            value.totalDurationSeconds() == 0 ? "No timed activity recorded" : duration(BigDecimal.valueOf(value.totalDurationSeconds())) + " timed activity",
            integerComparison(value.workoutCount(), previous.workouts().workoutCount(), "sessions", "last week"),
            integerComparison(value.workoutCount(), yearAgo.workouts().workoutCount(), "sessions", "52 weeks ago")
        );
    }

    private MetricCard decisionCard(Summary current, Summary previous, Summary yearAgo) {
        DecisionMetrics value = current.decisions();
        long total = value.wins() + value.misses();
        return new MetricCard(
            "Decision win rate",
            value.winRate() == null ? "Not recorded" : percentage(value.winRate()),
            total == 0 ? "No decisions recorded" : value.wins() + " wins, " + value.misses() + " misses",
            decisionComparison(value, previous.decisions(), "last week"),
            decisionComparison(value, yearAgo.decisions(), "52 weeks ago")
        );
    }

    private Comparison routineComparison(RoutineCompletion current, RoutineCompletion baseline, String label) {
        if (current.percentage() == null || baseline.percentage() == null) {
            return unknownComparison(label);
        }
        BigDecimal change = current.percentage().subtract(baseline.percentage());
        return comparison(signed(change, 1) + " pp vs " + label, change, 1, ImprovementDirection.HIGHER);
    }

    private Comparison calorieComparison(CalorieSummary current, CalorieSummary baseline, String label) {
        if (current.averageCalories() == null || baseline.averageCalories() == null) {
            return unknownComparison(label);
        }
        BigDecimal change = current.averageCalories().subtract(baseline.averageCalories());
        String text = signed(change, 0) + " kcal/day vs " + label + " · " + baseline.entryCount() + "/7 days";
        return comparison(text, change, 0, ImprovementDirection.LOWER);
    }

    private Comparison sleepComparison(AverageSleep current, AverageSleep baseline, String label) {
        if (current == null || baseline == null) {
            return unknownComparison(label);
        }
        BigDecimal change = current.totalSleepSeconds().subtract(baseline.totalSleepSeconds());
        String text = signedSleepDuration(change) + " vs " + label + " · " + baseline.nightCount() + "/7 nights";
        return comparison(text, roundedMinutes(change), ImprovementDirection.HIGHER);
    }

    private Comparison weightComparison(Weight current, Weight baseline, String label) {
        if (current == null || baseline == null) {
            return unknownComparison(label);
        }
        BigDecimal change = current.getWeight().subtract(baseline.getWeight());
        String text = signed(change, 1) + " kg vs " + label + " · " + measuredDate(baseline.getMeasuredAt());
        return comparison(text, change, 1, ImprovementDirection.LOWER);
    }

    private Comparison bloodPressureComparison(BloodPressure current, BloodPressure baseline, String label) {
        if (current == null || baseline == null) {
            return unknownComparison(label);
        }
        BigDecimal systolicChange = BigDecimal.valueOf(current.getUpper() - baseline.getUpper());
        BigDecimal diastolicChange = BigDecimal.valueOf(current.getLower() - baseline.getLower());
        String text = signed(systolicChange, 0) + " / " + signed(diastolicChange, 0) + " mmHg vs " + label
            + " · " + measuredDate(baseline.getMeasuredAt());
        ComparisonStatus systolicStatus = comparisonStatus(systolicChange.setScale(0, RoundingMode.HALF_UP), ImprovementDirection.LOWER);
        ComparisonStatus diastolicStatus = comparisonStatus(diastolicChange.setScale(0, RoundingMode.HALF_UP), ImprovementDirection.LOWER);
        return new Comparison(text, combinedStatus(systolicStatus, diastolicStatus));
    }

    private Comparison decisionComparison(DecisionMetrics current, DecisionMetrics baseline, String label) {
        if (current.winRate() == null || baseline.winRate() == null) {
            return unknownComparison(label);
        }
        BigDecimal change = current.winRate().subtract(baseline.winRate());
        return comparison(signed(change, 1) + " pp vs " + label, change, 1, ImprovementDirection.HIGHER);
    }

    private Comparison decimalComparison(BigDecimal current, BigDecimal baseline, String unit, String label, String coverage) {
        if (current == null || baseline == null) {
            return unknownComparison(label);
        }
        BigDecimal change = current.subtract(baseline);
        String text = signed(change, 1) + " " + unit + " vs " + label + " · " + coverage;
        return comparison(text, change, 1, ImprovementDirection.HIGHER);
    }

    private Comparison integerComparison(int current, int baseline, String unit, String label) {
        int change = current - baseline;
        String text = (change > 0 ? "+" : "") + change + " " + unit + " vs " + label;
        return comparison(text, BigDecimal.valueOf(change), ImprovementDirection.HIGHER);
    }

    private Comparison comparison(String text, BigDecimal change, int scale, ImprovementDirection direction) {
        return comparison(text, change.setScale(scale, RoundingMode.HALF_UP), direction);
    }

    private Comparison comparison(String text, BigDecimal roundedChange, ImprovementDirection direction) {
        return new Comparison(text, comparisonStatus(roundedChange, direction));
    }

    private Comparison unknownComparison(String label) {
        return new Comparison("No data " + label, ComparisonStatus.UNKNOWN);
    }

    private ComparisonStatus comparisonStatus(BigDecimal roundedChange, ImprovementDirection direction) {
        if (roundedChange.signum() == 0) {
            return ComparisonStatus.UNCHANGED;
        }
        boolean improved = direction == ImprovementDirection.HIGHER ? roundedChange.signum() > 0 : roundedChange.signum() < 0;
        return improved ? ComparisonStatus.IMPROVED : ComparisonStatus.WORSENED;
    }

    private ComparisonStatus combinedStatus(ComparisonStatus first, ComparisonStatus second) {
        if (first == second) {
            return first;
        }
        if (first == ComparisonStatus.UNCHANGED) {
            return second;
        }
        if (second == ComparisonStatus.UNCHANGED) {
            return first;
        }
        return ComparisonStatus.UNCHANGED;
    }

    private List<CardRow> rows(List<MetricCard> cards) {
        List<CardRow> rows = new ArrayList<>();
        for (int index = 0; index < cards.size(); index += 2) {
            rows.add(new CardRow(cards.get(index), index + 1 < cards.size() ? cards.get(index + 1) : null));
        }
        return rows;
    }

    private String percentage(BigDecimal value) {
        return decimal(value, 0) + "%";
    }

    private String whole(BigDecimal value) {
        return decimal(value, 0);
    }

    private String decimal(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String signed(BigDecimal value, int scale) {
        BigDecimal rounded = value.setScale(scale, RoundingMode.HALF_UP);
        return (rounded.signum() > 0 ? "+" : "") + rounded.toPlainString();
    }

    private String duration(BigDecimal seconds) {
        long totalMinutes = seconds.divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP).longValue();
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours == 0 ? minutes + " min" : hours + " h " + minutes + " min";
    }

    private String signedDuration(BigDecimal seconds) {
        long totalMinutes = roundedMinutes(seconds).longValue();
        long absoluteMinutes = Math.abs(totalMinutes);
        long hours = absoluteMinutes / 60;
        long minutes = absoluteMinutes % 60;
        String value = hours == 0 ? minutes + " min" : hours + " h " + minutes + " min";
        return (totalMinutes > 0 ? "+" : totalMinutes < 0 ? "−" : "") + value;
    }

    private String sleepDuration(BigDecimal seconds) {
        if (seconds.compareTo(BigDecimal.valueOf(3600)) < 0) {
            return roundedMinutes(seconds) + " min";
        }
        return decimal(seconds.divide(BigDecimal.valueOf(3600), 1, RoundingMode.HALF_UP), 1) + " h";
    }

    private String signedSleepDuration(BigDecimal seconds) {
        String sign = seconds.signum() > 0 ? "+" : seconds.signum() < 0 ? "−" : "";
        return sign + sleepDuration(seconds.abs());
    }

    private BigDecimal roundedMinutes(BigDecimal seconds) {
        return seconds.divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
    }

    private String dayCoverage(int count) {
        return count + " of 7 days recorded";
    }

    private String measurementDate(java.time.OffsetDateTime measuredAt) {
        return measuredAt == null ? "No measurement recorded" : "Measured " + formattedMeasurementDate(measuredAt);
    }

    private String measuredDate(java.time.OffsetDateTime measuredAt) {
        return "measured " + formattedMeasurementDate(measuredAt);
    }

    private String formattedMeasurementDate(java.time.OffsetDateTime measuredAt) {
        return DateTimes.toLocalDate(measuredAt).format(SUBJECT_DATE);
    }

    private enum ImprovementDirection {
        HIGHER,
        LOWER
    }
}
