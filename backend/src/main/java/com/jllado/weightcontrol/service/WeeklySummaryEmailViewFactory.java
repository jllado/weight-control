package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageBloodPressure;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageSleep;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageWeight;
import com.jllado.weightcontrol.service.WeeklyMetrics.CalorieSummary;
import com.jllado.weightcontrol.service.WeeklyMetrics.DecisionMetrics;
import com.jllado.weightcontrol.service.WeeklyMetrics.Progress;
import com.jllado.weightcontrol.service.WeeklyMetrics.RoutineCompletion;
import com.jllado.weightcontrol.service.WeeklyMetrics.Summary;
import com.jllado.weightcontrol.service.WeeklyMetrics.WorkoutSummary;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.CardRow;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.DayView;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.MetricCard;
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

    public WeeklySummaryEmailView create(User user, Progress progress, String appUrl) {
        Summary current = progress.currentPeriod();
        Summary previous = progress.previousComparablePeriod();
        Summary yearAgo = progress.yearAgoComparablePeriod();
        RoutineCompletion routines = current.routineCompletion();
        String dateRange = current.startDate().format(SUBJECT_DATE) + " – " + current.endDate().format(RANGE_END_DATE);
        List<MetricCard> cards = List.of(
            calorieCard(current, previous, yearAgo),
            sleepCard(current, previous, yearAgo),
            moodCard(current, previous, yearAgo),
            weightCard(current, previous, yearAgo),
            bloodPressureCard(current, previous, yearAgo),
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
            value == null ? "Not recorded" : duration(value.totalSleepSeconds()),
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

    private MetricCard weightCard(Summary current, Summary previous, Summary yearAgo) {
        AverageWeight value = current.weight();
        return new MetricCard(
            "Average weight",
            value == null ? "Not recorded" : decimal(value.weightKg(), 1) + " kg",
            measurementCoverage(value == null ? 0 : value.measurementCount()),
            weightComparison(value, previous.weight(), "last week"),
            weightComparison(value, yearAgo.weight(), "52 weeks ago")
        );
    }

    private MetricCard bloodPressureCard(Summary current, Summary previous, Summary yearAgo) {
        AverageBloodPressure value = current.bloodPressure();
        return new MetricCard(
            "Average blood pressure",
            value == null ? "Not recorded" : whole(value.systolic()) + " / " + whole(value.diastolic()) + " mmHg",
            measurementCoverage(value == null ? 0 : value.measurementCount()),
            bloodPressureComparison(value, previous.bloodPressure(), "last week"),
            bloodPressureComparison(value, yearAgo.bloodPressure(), "52 weeks ago")
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

    private String routineComparison(RoutineCompletion current, RoutineCompletion baseline, String label) {
        if (current.percentage() == null || baseline.percentage() == null) {
            return "No data " + label;
        }
        return signed(current.percentage().subtract(baseline.percentage()), 1) + " pp vs " + label;
    }

    private String calorieComparison(CalorieSummary current, CalorieSummary baseline, String label) {
        if (current.averageCalories() == null || baseline.averageCalories() == null) {
            return "No data " + label;
        }
        return signed(current.averageCalories().subtract(baseline.averageCalories()), 0) + " kcal/day vs " + label + " · " + baseline.entryCount() + "/7 days";
    }

    private String sleepComparison(AverageSleep current, AverageSleep baseline, String label) {
        if (current == null || baseline == null) {
            return "No data " + label;
        }
        return signedDuration(current.totalSleepSeconds().subtract(baseline.totalSleepSeconds())) + " vs " + label + " · " + baseline.nightCount() + "/7 nights";
    }

    private String weightComparison(AverageWeight current, AverageWeight baseline, String label) {
        if (current == null || baseline == null) {
            return "No data " + label;
        }
        return signed(current.weightKg().subtract(baseline.weightKg()), 1) + " kg vs " + label + " · " + measurements(baseline.measurementCount());
    }

    private String bloodPressureComparison(AverageBloodPressure current, AverageBloodPressure baseline, String label) {
        if (current == null || baseline == null) {
            return "No data " + label;
        }
        return signed(current.systolic().subtract(baseline.systolic()), 0) + " / "
            + signed(current.diastolic().subtract(baseline.diastolic()), 0) + " mmHg vs " + label
            + " · " + measurements(baseline.measurementCount());
    }

    private String decisionComparison(DecisionMetrics current, DecisionMetrics baseline, String label) {
        if (current.winRate() == null || baseline.winRate() == null) {
            return "No data " + label;
        }
        return signed(current.winRate().subtract(baseline.winRate()), 1) + " pp vs " + label;
    }

    private String decimalComparison(BigDecimal current, BigDecimal baseline, String unit, String label, String coverage) {
        if (current == null || baseline == null) {
            return "No data " + label;
        }
        return signed(current.subtract(baseline), 1) + " " + unit + " vs " + label + " · " + coverage;
    }

    private String integerComparison(int current, int baseline, String unit, String label) {
        int change = current - baseline;
        return (change > 0 ? "+" : "") + change + " " + unit + " vs " + label;
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
        long totalMinutes = seconds.divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP).longValue();
        long absoluteMinutes = Math.abs(totalMinutes);
        long hours = absoluteMinutes / 60;
        long minutes = absoluteMinutes % 60;
        String value = hours == 0 ? minutes + " min" : hours + " h " + minutes + " min";
        return (totalMinutes > 0 ? "+" : totalMinutes < 0 ? "−" : "") + value;
    }

    private String dayCoverage(int count) {
        return count + " of 7 days recorded";
    }

    private String measurementCoverage(int count) {
        return count + (count == 1 ? " measurement recorded" : " measurements recorded");
    }

    private String measurements(int count) {
        return count + (count == 1 ? " measurement" : " measurements");
    }
}
