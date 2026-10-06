package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.domain.PersonalRecordUnit;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.HistoryEventResponse;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageSleep;
import com.jllado.weightcontrol.service.WeeklyMetrics.AverageStatus;
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
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.OutcomeView;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.ReflectionSectionView;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.RecordView;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.RoutineWatchout;
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.WeeklyReflectionView;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.BloodPressureMeasurement;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.BackPainSummary;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.PersonalRecordSnapshot;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.WeightMeasurement;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class WeeklySummaryEmailViewFactory {

    private static final DateTimeFormatter SUBJECT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter RANGE_END_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public WeeklySummaryEmailView create(User user, Progress progress, WeeklySummaryMeasurements measurements, String appUrl) {
        return create(user, progress, measurements, List.of(), appUrl);
    }

    public WeeklySummaryEmailView create(User user, Progress progress, WeeklySummaryMeasurements measurements, List<HistoryEventResponse> records, String appUrl) {
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
            sicknessCard(current, previous, yearAgo),
            decisionCard(current, previous, yearAgo),
            dailyStatusCard(current, previous, yearAgo)
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
                    day.percentage() == null ? "—" : percentage(day.percentage()),
                    dayCssClass(day.percentage())
                ))
                .toList(),
            records.stream().map(this::recordView).toList(),
            List.of(),
            rows(cards),
            List.of(),
            null,
            null,
            appUrl
        );
    }

    public WeeklySummaryEmailView create(User user, WeeklySummarySnapshot snapshot, WeeklyReflection reflection, String appUrl) {
        Progress progress = snapshot.progress();
        Summary current = progress.currentPeriod();
        Summary previous = progress.previousComparablePeriod();
        Summary yearAgo = progress.yearAgoComparablePeriod();
        RoutineCompletion routines = current.routineCompletion();
        List<MetricCard> cards = List.of(
            calorieCard(current, previous, yearAgo),
            sleepCard(current, previous, yearAgo),
            moodCard(current, previous, yearAgo),
            averageWeightCard(current.weight(), previous.weight(), yearAgo.weight()),
            averageBloodPressureCard(current.bloodPressure(), previous.bloodPressure(), yearAgo.bloodPressure()),
            workoutCard(current, previous, yearAgo),
            backPainCard(snapshot.backPain()),
            sicknessCard(current, previous, yearAgo),
            decisionCard(current, previous, yearAgo),
            dailyStatusCard(current, previous, yearAgo)
        );
        OutcomeView outcomes = new OutcomeView(
            outcomeWeight(snapshot.outcomes().weight()),
            outcomeBloodPressure(snapshot.outcomes().bloodPressure())
        );
        return new WeeklySummaryEmailView(
            "Your Weight Control weekly summary — " + current.startDate().format(SUBJECT_DATE) + "–" + current.endDate().format(SUBJECT_DATE),
            user.getDisplayName() == null ? user.getEmail() : user.getDisplayName(),
            current.startDate().format(SUBJECT_DATE) + " – " + current.endDate().format(RANGE_END_DATE),
            routines.percentage() == null ? "Not applicable" : percentage(routines.percentage()),
            routines.opportunities() == 0 ? "No active routine opportunities" : routines.completed() + " of " + routines.opportunities() + " routine opportunities completed",
            routineComparison(routines, previous.routineCompletion(), "last week"),
            routineComparison(routines, yearAgo.routineCompletion(), "52 weeks ago"),
            routines.days().stream().map(day -> new DayView(
                day.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                day.percentage() == null ? "—" : percentage(day.percentage()),
                dayCssClass(day.percentage())
            )).toList(),
            snapshot.personalRecords().stream().map(this::recordView).toList(),
            snapshot.routines().stream()
                .filter(routine -> routine.completedDays() * 100 < routine.eligibleDays() * 60)
                .map(routine -> new RoutineWatchout(
                    routine.name(),
                    routine.completedDays(),
                    routine.eligibleDays(),
                    percentage(routine.percentage())
                ))
                .toList(),
            rows(cards),
            snapshot.warnings(),
            outcomes,
            reflection == null ? null : reflectionView(reflection),
            summaryUrl(appUrl, snapshot.fridayDate())
        );
    }

    private String summaryUrl(String appUrl, java.time.LocalDate fridayDate) {
        String base = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
        return base + "/weekly-summaries/" + fridayDate;
    }

    private MetricCard averageWeightCard(WeeklyMetrics.AverageWeight current, WeeklyMetrics.AverageWeight previous, WeeklyMetrics.AverageWeight yearAgo) {
        String value = current == null || current.weightKg() == null ? "Not recorded" : decimal(current.weightKg(), 1) + " kg average";
        List<String> bodyComposition = new ArrayList<>();
        if (current != null && (current.fatPercentage() != null || current.fatKg() != null)) {
            bodyComposition.add(bodyCompositionValue("fat", current.fatPercentage(), current.fatKg()));
        }
        if (current != null && (current.musclePercentage() != null || current.muscleKg() != null)) {
            bodyComposition.add(bodyCompositionValue("muscle", current.musclePercentage(), current.muscleKg()));
        }
        if (current != null) {
            bodyComposition.add(current.measurementCount() + " readings this week");
        }
        return new MetricCard(
            "Average weight and body composition",
            value,
            bodyComposition.isEmpty() ? "No weight measurements recorded" : String.join(" · ", bodyComposition),
            averageWeightComparison(current, previous, "last week"),
            averageWeightComparison(current, yearAgo, "52 weeks ago")
        );
    }

    private Comparison averageWeightComparison(WeeklyMetrics.AverageWeight current, WeeklyMetrics.AverageWeight baseline, String label) {
        if (current == null || baseline == null || current.weightKg() == null || baseline.weightKg() == null) {
            return unknownComparison(label);
        }
        BigDecimal change = current.weightKg().subtract(baseline.weightKg());
        return comparison(signed(change, 1) + " kg vs " + label + " · " + baseline.measurementCount() + " readings", change, 1, ImprovementDirection.LOWER);
    }

    private MetricCard averageBloodPressureCard(WeeklyMetrics.AverageBloodPressure current, WeeklyMetrics.AverageBloodPressure previous, WeeklyMetrics.AverageBloodPressure yearAgo) {
        return new MetricCard(
            "Average blood pressure",
            current == null ? "Not recorded" : decimal(current.systolic(), 0) + " / " + decimal(current.diastolic(), 0) + " mmHg",
            current == null ? "No readings this week" : current.measurementCount() + " readings this week",
            averageBloodPressureComparison(current, previous, "last week"),
            averageBloodPressureComparison(current, yearAgo, "52 weeks ago")
        );
    }

    private Comparison averageBloodPressureComparison(WeeklyMetrics.AverageBloodPressure current, WeeklyMetrics.AverageBloodPressure baseline, String label) {
        if (current == null || baseline == null) {
            return unknownComparison(label);
        }
        BigDecimal systolic = current.systolic().subtract(baseline.systolic());
        BigDecimal diastolic = current.diastolic().subtract(baseline.diastolic());
        ComparisonStatus status = combinedStatus(
            comparisonStatus(systolic.setScale(0, RoundingMode.HALF_UP), ImprovementDirection.LOWER),
            comparisonStatus(diastolic.setScale(0, RoundingMode.HALF_UP), ImprovementDirection.LOWER)
        );
        return new Comparison(signed(systolic, 0) + " / " + signed(diastolic, 0) + " mmHg vs " + label + " · " + baseline.measurementCount() + " readings", status);
    }

    private String outcomeWeight(WeightMeasurement value) {
        if (value == null) return "No measurement recorded";
        return decimal(value.weightKg(), 1) + " kg · "
            + bodyCompositionValue("fat", value.fatPercentage(), value.fatKg()) + " · "
            + bodyCompositionValue("muscle", value.musclePercentage(), value.muscleKg()) + " · "
            + value.measuredDate().format(RANGE_END_DATE);
    }

    private String outcomeBloodPressure(BloodPressureMeasurement value) {
        return value == null ? "No measurement recorded" : value.systolic() + " / " + value.diastolic() + " mmHg · " + value.measuredDate().format(RANGE_END_DATE);
    }

    private String bodyCompositionValue(String label, BigDecimal percentage, BigDecimal kilograms) {
        List<String> values = new ArrayList<>();
        if (percentage != null) values.add(decimal(percentage, 1) + "%");
        if (kilograms != null) values.add(decimal(kilograms, 1) + " kg");
        return label + " " + (values.isEmpty() ? "not recorded" : String.join(" · ", values));
    }

    private WeeklyReflectionView reflectionView(WeeklyReflection reflection) {
        return new WeeklyReflectionView(
            reflection.getTitle(),
            reflection.getSummary(),
            List.of(
                new ReflectionSectionView("Body composition", reflection.getBodyCompositionSummary(), reflection.getBodyCompositionNextAction()),
                new ReflectionSectionView("Blood pressure", reflection.getBloodPressureSummary(), reflection.getBloodPressureNextAction()),
                new ReflectionSectionView("Routines", reflection.getRoutinesSummary(), reflection.getRoutinesNextAction()),
                new ReflectionSectionView("Nutrition", reflection.getNutritionSummary(), reflection.getNutritionNextAction()),
                new ReflectionSectionView("Training and recovery", reflection.getTrainingRecoverySummary(), reflection.getTrainingRecoveryNextAction()),
                new ReflectionSectionView("Goal progress", reflection.getGoalProgressSummary(), reflection.getGoalProgressNextAction())
            ),
            reflection.getNextWeekActions()
        );
    }

    private RecordView recordView(PersonalRecordSnapshot record) {
        return new RecordView(record.label(), recordValue(record.value(), record.unit()), record.date().format(SUBJECT_DATE));
    }

    private MetricCard calorieCard(Summary current, Summary previous, Summary yearAgo) {
        CalorieSummary value = current.calories();
        return new MetricCard(
            "Average nutrition",
            value.averageCalories() == null ? "Not recorded" : whole(value.averageCalories()) + " kcal/day",
            String.join(" · ", dayCoverage(value.entryCount()),
                averageMacro("Protein", value.averageProteinGrams(), value.proteinDayCount()),
                averageMacro("Carbohydrate", value.averageCarbohydrateGrams(), value.carbohydrateDayCount()),
                averageMacro("Fat", value.averageFatGrams(), value.fatDayCount())),
            calorieComparison(value, previous.calories(), "last week"),
            calorieComparison(value, yearAgo.calories(), "52 weeks ago")
        );
    }

    private String averageMacro(String label, BigDecimal average, int dayCount) {
        return average == null ? label + " not recorded" : label + " " + decimal(average, 1) + " g/day (" + dayCount + " days)";
    }

    private MetricCard sleepCard(Summary current, Summary previous, Summary yearAgo) {
        AverageSleep value = current.sleep();
        return new MetricCard(
            "Average sleep",
            value == null ? "Not recorded" : sleepDuration(value.totalSleepSeconds()),
            value == null ? "0 of 7 nights recorded" : sleepDetails(value),
            sleepComparison(value, previous.sleep(), "last week"),
            sleepComparison(value, yearAgo.sleep(), "52 weeks ago")
        );
    }

    private String sleepDetails(AverageSleep sleep) {
        List<String> details = new ArrayList<>();
        details.add(sleep.nightCount() + " of 7 nights recorded");
        if (sleep.deepSleepSeconds() != null) details.add("Deep " + sleepDuration(sleep.deepSleepSeconds()));
        if (sleep.remSleepSeconds() != null) details.add("REM " + sleepDuration(sleep.remSleepSeconds()));
        if (sleep.lightSleepSeconds() != null) details.add("Light " + sleepDuration(sleep.lightSleepSeconds()));
        if (sleep.awakeSeconds() != null) details.add("Awake " + sleepDuration(sleep.awakeSeconds()));
        if (sleep.averageHeartRate() != null) details.add("Average heart rate " + decimal(sleep.averageHeartRate(), 0) + " bpm");
        if (sleep.averageHrv() != null) details.add("Average HRV " + decimal(sleep.averageHrv(), 0) + " ms");
        return String.join(" · ", details);
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
            String.join(" · ", List.of(
                recordedWorkoutMetric(value.durationReadingCount(), "Timed activity", duration(BigDecimal.valueOf(value.totalDurationSeconds()))),
                recordedWorkoutMetric(value.distanceReadingCount(), "Distance", decimal(value.totalDistanceKm(), 1) + " km"),
                recordedWorkoutMetric(value.calorieReadingCount(), "Workout energy", whole(BigDecimal.valueOf(value.totalCalories())) + " kcal"),
                recordedWorkoutMetric(value.strengthSetCount(), "Strength volume", decimal(value.strengthVolumeKg(), 1) + " kg", "sets")
            )),
            integerComparison(value.workoutCount(), previous.workouts().workoutCount(), "sessions", "last week"),
            integerComparison(value.workoutCount(), yearAgo.workouts().workoutCount(), "sessions", "52 weeks ago")
        );
    }

    private String recordedWorkoutMetric(int readingCount, String label, String value) {
        return recordedWorkoutMetric(readingCount, label, value, "records");
    }

    private String recordedWorkoutMetric(int readingCount, String label, String value, String unit) {
        return readingCount == 0 ? label + " not recorded" : label + ": " + value + " (" + readingCount + " " + unit + ")";
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

    private MetricCard dailyStatusCard(Summary current, Summary previous, Summary yearAgo) {
        AverageStatus value = current.dashboard();
        return new MetricCard(
            "Daily status completion",
            value == null ? "Not recorded" : "Average across eligible check-ins",
            value == null ? "No daily status records" : averageStatus(value),
            averageStatusComparison(value, previous.dashboard(), "last week"),
            averageStatusComparison(value, yearAgo.dashboard(), "52 weeks ago")
        );
    }

    private String averageStatus(AverageStatus value) {
        return String.join(" · ",
            statusPercentage("Routines", value.routinesPercentage()),
            statusPercentage("Weight", value.weightPercentage()),
            statusPercentage("Blood pressure", value.bloodPressurePercentage()),
            statusPercentage("Flexibility", value.flexibilityPercentage()),
            statusPercentage("Mind", value.mindPercentage())
        );
    }

    private String statusPercentage(String label, BigDecimal value) {
        return label + " " + (value == null ? "not recorded" : percentage(value));
    }

    private Comparison averageStatusComparison(AverageStatus current, AverageStatus baseline, String label) {
        if (current == null || baseline == null) return unknownComparison(label);
        return new Comparison(
            "Routines " + statusChange(current.routinesPercentage(), baseline.routinesPercentage())
                + " · weight " + statusChange(current.weightPercentage(), baseline.weightPercentage())
                + " · blood pressure " + statusChange(current.bloodPressurePercentage(), baseline.bloodPressurePercentage())
                + " · flexibility " + statusChange(current.flexibilityPercentage(), baseline.flexibilityPercentage())
                + " · mind " + statusChange(current.mindPercentage(), baseline.mindPercentage())
                + " vs " + label,
            ComparisonStatus.UNKNOWN
        );
    }

    private String statusChange(BigDecimal current, BigDecimal baseline) {
        return current == null || baseline == null ? "unknown" : signed(current.subtract(baseline), 1) + " pp";
    }

    private MetricCard sicknessCard(Summary current, Summary previous, Summary yearAgo) {
        long count = current.sicknessesByType().values().stream().mapToLong(Long::longValue).sum();
        return new MetricCard(
            "Sickness records",
            count == 0 ? "No sicknesses recorded" : count + (count == 1 ? " record" : " records"),
            sicknessDetail(current),
            sicknessComparison(current, previous, "last week"),
            sicknessComparison(current, yearAgo, "52 weeks ago")
        );
    }

    private MetricCard backPainCard(BackPainSummary summary) {
        if (summary == null) {
            return new MetricCard("Back pain", "Not included in this saved snapshot", "Historical snapshots remain unchanged", unknownComparison("last week"), unknownComparison("52 weeks ago"));
        }
        String value = summary.checkInCount() == 0 ? "No check-ins recorded"
            : summary.episodeCount() == 0 ? "No pain reported" : summary.episodeCount() + " episodes · " + summary.painDayCount() + " days";
        String severity = formatCounts(summary.episodesBySeverity());
        String regions = formatCounts(summary.episodesByRegion());
        String sides = formatCounts(summary.episodesBySide());
        List<String> details = new ArrayList<>();
        details.add(summary.checkInCount() + " check-ins");
        if (!severity.isEmpty()) details.add("Severity: " + severity);
        if (!regions.isEmpty()) details.add("Regions: " + regions);
        if (!sides.isEmpty()) details.add("Sides: " + sides);
        return new MetricCard(
            "Back pain",
            value,
            String.join(" · ", details),
            unknownComparison("last week"),
            unknownComparison("52 weeks ago")
        );
    }

    private String formatCounts(Map<? extends Enum<?>, Integer> counts) {
        return counts.entrySet().stream()
            .sorted((first, second) -> Integer.compare(first.getKey().ordinal(), second.getKey().ordinal()))
            .map(entry -> titleCase(entry.getKey().name()) + ": " + entry.getValue())
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private String titleCase(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String sicknessDetail(Summary summary) {
        if (summary.sicknessesByType().isEmpty()) return "No sickness records in this week";
        String types = summary.sicknessesByType().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(java.util.stream.Collectors.joining(", "));
        String severity = summary.sicknessesBySeverity().entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(java.util.stream.Collectors.joining(", "));
        return "Types: " + types + (severity.isEmpty() ? "" : " · Severity: " + severity);
    }

    private Comparison sicknessComparison(Summary current, Summary baseline, String label) {
        long currentCount = current.sicknessesByType().values().stream().mapToLong(Long::longValue).sum();
        long baselineCount = baseline.sicknessesByType().values().stream().mapToLong(Long::longValue).sum();
        return new Comparison(currentCount + " recorded vs " + baselineCount + " " + label, ComparisonStatus.UNKNOWN);
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

    private String dayCssClass(BigDecimal percentage) {
        if (percentage == null) {
            return "day-value--unknown";
        }
        if (percentage.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "day-value--perfect";
        }
        if (percentage.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "day-value--good";
        }
        if (percentage.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "day-value--normal";
        }
        if (percentage.compareTo(BigDecimal.valueOf(40)) >= 0) {
            return "day-value--fail";
        }
        return "day-value--bad";
    }

    private RecordView recordView(HistoryEventResponse record) {
        String label = record.metricLabel();
        if (!record.subject().type().equals(record.domain().name())) {
            label += " — " + record.subject().label();
        }
        return new RecordView(label, recordValue(record.value(), record.unit()), record.recordDate().format(SUBJECT_DATE));
    }

    private String recordValue(BigDecimal value, PersonalRecordUnit unit) {
        String number = value.stripTrailingZeros().toPlainString();
        return switch (unit) {
            case KG -> number + " kg";
            case PERCENT -> number + "%";
            case REPETITIONS -> number + " reps";
            case SECONDS -> String.format(Locale.ENGLISH, "%02d:%02d", value.longValue() / 60, value.longValue() % 60);
            case KM_PER_HOUR -> number + " km/h";
            case RPM -> number + " RPM";
            case KM -> number + " km";
            case LEVEL -> "Level " + number;
            case MM_HG -> number + " mm Hg";
            case MG_PER_DL -> number + " mg/dL";
            case KCAL -> number + " kcal";
            case GRAMS -> number + " g";
            case BPM -> number + " bpm";
            case MILLISECONDS -> number + " ms";
            case SCORE_OUT_OF_FIVE -> number + "/5";
            case COMPLETIONS -> number + " completion" + (value.compareTo(BigDecimal.ONE) == 0 ? "" : "s");
            case DAYS -> number + " day" + (value.compareTo(BigDecimal.ONE) == 0 ? "" : "s");
            case DECISIONS -> number + " decision" + (value.compareTo(BigDecimal.ONE) == 0 ? "" : "s");
            case COUNT, SCORE -> number;
            case KG_PER_SQUARE_METER -> number + " kg/m²";
            case KG_REPETITIONS -> number + " kg·reps";
        };
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
