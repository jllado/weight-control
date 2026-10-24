package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse;
import com.jllado.weightcontrol.domain.MacroTargetComparison;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class WeeklySummaryPresentationMapper {
    private WeeklySummaryPresentationMapper() { }
    public record Presentation(List<Highlight> highlights, List<WeeklyRecommendationMapper.Recommendation> recommendations,
        List<NutritionRow> nutritionRows, List<String> periods) { }
    public record Highlight(String key, String label, String value, String comparison, BigDecimal currentAmount,
        BigDecimal previousAmount, String coverage, int currentWidth, int previousWidth) { }
    public record NutritionRow(String label, String current, String previous, String yearAgo) { }

    public static Presentation create(WeeklySummarySnapshot snapshot, WeeklyReflectionResponse reflection, String source) {
        var current = snapshot.progress().currentPeriod();
        var previous = snapshot.progress().previousComparablePeriod();
        List<Highlight> highlights = List.of(
            highlight("sleep", "Average sleep", minutes(current.sleep()), minutes(previous.sleep()), "min",
                current.sleep() == null ? "No nights recorded" : current.sleep().nightCount() + (current.sleep().nightCount() == 1 ? " night" : " nights")),
            highlight("calories", "Daily calories", current.calories().averageCalories(), previous.calories().averageCalories(), "kcal",
                current.calories().entryCount() + (current.calories().entryCount() == 1 ? " recorded day" : " recorded days")),
            highlight("workouts", "Workouts", BigDecimal.valueOf(current.workouts().workoutCount()), BigDecimal.valueOf(previous.workouts().workoutCount()), "sessions", "Recorded sessions"),
            highlight("routines", "Routines", current.routineCompletion().percentage(), previous.routineCompletion().percentage(), "pp",
                current.routineCompletion().completed() + "/" + current.routineCompletion().opportunities() + " opportunities"),
            new Highlight("records", "Personal records", snapshot.personalRecords().size() + " new", "",
                BigDecimal.valueOf(snapshot.personalRecords().size()), null,
                snapshot.personalRecords().isEmpty() ? "No new records this week" : snapshot.personalRecords().stream()
                    .map(record -> record.label() + (record.subjectLabel() == null ? "" : " — " + record.subjectLabel()) + " · " + record.date())
                    .collect(java.util.stream.Collectors.joining("; ")), 0, 0)
        );
        var recommendations = reflection == null ? List.<WeeklyRecommendationMapper.Recommendation>of()
            : WeeklyRecommendationMapper.from(source, reflection);
        var nutrition = snapshot.nutrition();
        var rows = nutrition == null ? List.<NutritionRow>of() : nutrition.current().measures().stream().map(measure ->
            new NutritionRow(measure.label(), nutrient(measure), nutrient(find(nutrition.previous(), measure.key())),
                nutrient(find(nutrition.yearAgo(), measure.key())))).toList();
        return new Presentation(highlights, recommendations, rows, List.of(
            "Current: " + current.startDate() + " – " + current.endDate(),
            "Previous week: " + previous.startDate() + " – " + previous.endDate(),
            "52 weeks ago: " + snapshot.progress().yearAgoComparablePeriod().startDate() + " – " + snapshot.progress().yearAgoComparablePeriod().endDate()));
    }
    private static WeeklyNutritionSnapshot.Measure find(WeeklyNutritionSnapshot.Period period, String key) {
        return period.measures().stream().filter(measure -> measure.key().equals(key)).findFirst().orElseThrow();
    }
    private static String nutrient(WeeklyNutritionSnapshot.Measure measure) {
        String intake = measure.average() == null ? "Not available" : measure.average().stripTrailingZeros().toPlainString() + " " + measure.unit();
        String coverage = measure.eligibleDays() + (measure.eligibleDays() == 1 ? " complete day" : " complete days");
        if (measure.totalFoods() > 0) coverage += " · " + measure.coveredFoods() + "/" + measure.totalFoods() + " foods covered · " + measure.estimatedFoods() + " estimated";
        String target = measure.target() == null ? "Target unavailable" : "Current target "
            + (measure.comparison() == MacroTargetComparison.MINIMUM ? "≥" : "≤") + measure.target().stripTrailingZeros().toPlainString() + " " + measure.unit();
        return intake + " · " + coverage + " · " + target + (measure.unavailableReason() == null ? "" : " · " + measure.unavailableReason());
    }
    private static Highlight highlight(String key, String label, BigDecimal current, BigDecimal previous, String unit, String coverage) {
        String value = current == null ? "Not recorded" : key.equals("sleep")
            ? current.setScale(0, RoundingMode.HALF_UP).intValue() / 60 + " h " + current.setScale(0, RoundingMode.HALF_UP).intValue() % 60 + " min"
            : current.setScale(0, RoundingMode.HALF_UP).toPlainString() + (key.equals("routines") ? "%" : " " + unit(unit, current));
        String comparison = current == null || previous == null ? "Comparison not available"
            : signed(current.subtract(previous)) + " " + unit(unit, current.subtract(previous)) + " vs previous week";
        BigDecimal maximum = current == null ? previous : previous == null ? current : current.max(previous);
        return new Highlight(key, label, value, comparison, current, previous, coverage, width(current, maximum), width(previous, maximum));
    }
    private static int width(BigDecimal value, BigDecimal maximum) {
        return value == null || maximum.signum() == 0 ? 0 : value.multiply(BigDecimal.valueOf(100)).divide(maximum, 0, RoundingMode.HALF_UP).intValue();
    }
    private static String unit(String unit, BigDecimal value) {
        return unit.equals("sessions") && value.abs().compareTo(BigDecimal.ONE) == 0 ? "session" : unit;
    }
    private static String signed(BigDecimal value) {
        BigDecimal rounded = value.setScale(0, RoundingMode.HALF_UP);
        return (rounded.signum() > 0 ? "+" : "") + rounded.toPlainString();
    }
    private static BigDecimal minutes(WeeklyMetrics.AverageSleep sleep) {
        return sleep == null || sleep.totalSleepSeconds() == null ? null : sleep.totalSleepSeconds().divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP);
    }
}
