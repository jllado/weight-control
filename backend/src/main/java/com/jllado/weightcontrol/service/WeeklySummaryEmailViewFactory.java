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
import com.jllado.weightcontrol.service.WeeklySummaryEmailView.*;
import com.jllado.weightcontrol.domain.MacroTargetComparison;
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
import com.jllado.weightcontrol.service.WeeklySummarySnapshot.BackPainPeriodSummary;
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
            workoutCard(current),
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
            rowsWithWorkoutActivity(cards, current, previous, yearAgo),
            List.of(),
            null,
            null,
            appUrl,
            null,
            visualSummary(legacySnapshot(progress, measurements), null)
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
            workoutCard(current),
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
            withWeeklyRoutines(rowsWithWorkoutActivity(cards, current, previous, yearAgo), snapshot),
            snapshot.warnings(),
            outcomes,
            reflection == null ? null : reflectionView(reflection),
            summaryUrl(appUrl, snapshot.fridayDate()),
            WeeklySummaryPresentationMapper.create(snapshot,
                reflection == null ? null : com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse.from(reflection),
                reflection == null ? null : "weekly:" + reflection.getId()),
            visualSummary(snapshot, reflection == null ? null : com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse.from(reflection))
        );
    }

    private static final String MET_COLOR = "#2f855a";
    private static final String MISSED_COLOR = "#c05621";
    private static final String NEUTRAL_COLOR = "#486277";
    private static final String UNAVAILABLE = "Not available";

    private WeeklySummarySnapshot legacySnapshot(Progress progress, WeeklySummaryMeasurements measurements) {
        var period = measurements.currentPeriod();
        Weight weight = period.weight();
        BloodPressure pressure = period.bloodPressure();
        var outcomeWeight = weight == null ? null : new WeightMeasurement(DateTimes.toLocalDate(weight.getMeasuredAt()),
            weight.getWeight(), weight.getFatPercentage(), weight.getFat(), weight.getMusclePercentage(), weight.getMuscle());
        var outcomePressure = pressure == null ? null : new BloodPressureMeasurement(DateTimes.toLocalDate(pressure.getMeasuredAt()), pressure.getUpper(), pressure.getLower());
        return new WeeklySummarySnapshot(1, progress.currentPeriod().startDate(), progress.currentPeriod().endDate(), progress,
            new WeeklySummarySnapshot.OutcomeMeasurements(outcomeWeight, outcomePressure), List.of(), null, List.of(), List.of());
    }

    public VisualSummary visualSummary(WeeklySummarySnapshot snapshot, com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.WeeklyReflectionResponse reflection) {
        var current = snapshot.progress().currentPeriod();
        var previous = snapshot.progress().previousComparablePeriod();
        var year = snapshot.progress().yearAgoComparablePeriod();
        var goals = snapshot.emailGoals();
        var rules = goals == null ? null : goals.rules();
        var completion = current.routineCompletion();
        var attention = snapshot.routines().stream().filter(routine -> routine.eligibleDays() > 0
                && (long) routine.completedDays() * 100 < (long) routine.eligibleDays() * 60)
            .sorted(java.util.Comparator.comparing(WeeklySummarySnapshot.RoutineProgress::percentage).thenComparing(WeeklySummarySnapshot.RoutineProgress::name)).toList();
        var hero = new RoutineHero(completion.percentage() == null ? UNAVAILABLE : percentage(completion.percentage()), completion.completed(), completion.opportunities(),
            visualDelta(completion.percentage(), previous.routineCompletion().percentage(), "pp", 1, true),
            visualDelta(completion.percentage(), year.routineCompletion().percentage(), "pp", 1, true),
            completion.days().stream().map(day -> new DayBar(day.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                day.percentage() == null ? "—" : percentage(day.percentage()), day.percentage() == null ? 0 : day.percentage().setScale(0, RoundingMode.HALF_UP).intValue(),
                day.percentage() == null ? neutral("No opportunities") : new Status(day.percentage().compareTo(BigDecimal.valueOf(60)) < 0 ? "Needs attention" : "Recorded",
                    day.percentage().compareTo(BigDecimal.valueOf(60)) < 0 ? MISSED_COLOR : MET_COLOR))).toList(),
            attention.stream().limit(3).map(routine -> new RoutineAttention(routine.name(), routine.completedDays() + "/" + routine.eligibleDays() + " days · " + percentage(routine.percentage()))).toList(), attention.size());
        BigDecimal sleep = sleepSeconds(current), calorie = current.calories().averageCalories();
        BigDecimal weight = current.weight() == null ? null : current.weight().weightKg();
        BigDecimal fat = current.weight() == null ? null : current.weight().fatPercentage();
        var outcome = snapshot.outcomes().weight();
        var bp = snapshot.outcomes().bloodPressure();
        String sleepDetail = current.sleep() == null ? "No nights recorded" : current.sleep().nightCount() + "/7 nights · "
            + (rules == null ? "Target unavailable" : "Goal ≥ " + duration(BigDecimal.valueOf(rules.sleepMinimumSeconds())) + " · " + rules.sleepBasis());
        var cards = List.of(
            new OverviewCard("sleep", "Average sleep", sleep == null ? UNAVAILABLE : duration(sleep), sleepDetail,
                sleepStatus(current.sleep(), rules),
                visualDelta(minutes(sleep), minutes(sleepSeconds(previous)), "min", 0, false), visualDelta(minutes(sleep), minutes(sleepSeconds(year)), "min", 0, false)),
            new OverviewCard("calories", "Calories/day", amount(calorie, "kcal", 0), current.calories().entryCount() + "/7 days · "
                + (goals == null ? "Target unavailable" : "Goal ≤ " + amount(goals.weeklyAverageCalorieMaximum(), "kcal/day", 0)),
                calorie == null ? neutral(UNAVAILABLE) : goals == null ? neutral("Target unavailable") : goalStatus(calorie, goals.weeklyAverageCalorieMaximum(), true, "Within calorie ceiling", "Above calorie ceiling"),
                visualDelta(calorie, previous.calories().averageCalories(), "kcal/day", 0, false), visualDelta(calorie, year.calories().averageCalories(), "kcal/day", 0, false)),
            new OverviewCard("weight", "Average weight", amount(weight, "kg", 1), bodyFatDetail(fat, goals)
                + (current.weight() == null ? "" : " · " + current.weight().measurementCount() + " readings"), bodyFatStatus(fat, goals),
                visualDelta(weight, previous.weight() == null ? null : previous.weight().weightKg(), "kg", 1, false),
                visualDelta(weight, year.weight() == null ? null : year.weight().weightKg(), "kg", 1, false)),
            new OverviewCard("mood", "Average mood", amount(current.moodAverage(), "/5", 1), current.moodDayCount() + "/7 days recorded", moodStatus(current.moodAverage(), rules),
                visualDelta(current.moodAverage(), previous.moodAverage(), "points", 1, false), visualDelta(current.moodAverage(), year.moodAverage(), "points", 1, false)),
            new OverviewCard("weekendWeight", "Weekend weight", outcome == null ? UNAVAILABLE : amount(outcome.weightKg(), "kg", 1),
                outcome == null ? "Friday–Sunday · No measurement" : outcome.measuredDate().format(RANGE_END_DATE) + " · " + bodyFatDetail(outcome.fatPercentage(), goals),
                bodyFatStatus(outcome == null ? null : outcome.fatPercentage(), goals), unavailableDelta(), unavailableDelta()),
            new OverviewCard("bloodPressure", "Weekend blood pressure", bp == null ? UNAVAILABLE : bp.systolic() + "/" + bp.diastolic() + " mm Hg",
                bp == null ? "Friday–Sunday · No measurement" : "Measured " + bp.measuredDate().format(RANGE_END_DATE), pressureStatus(bp, rules), unavailableDelta(), unavailableDelta())
        );
        var workouts = List.of(
            workoutRow("Total sessions", BigDecimal.valueOf(current.workouts().workoutCount()), BigDecimal.valueOf(previous.workouts().workoutCount()), BigDecimal.valueOf(year.workouts().workoutCount()), "", false, false, false, snapshot.workoutTargets(), WeeklyWorkoutTargets.Period::totalSessions),
            workoutRow("Strength sessions", number(current.workouts().strengthSessionCount()), number(previous.workouts().strengthSessionCount()), number(year.workouts().strengthSessionCount()), "", false, false, false, snapshot.workoutTargets(), WeeklyWorkoutTargets.Period::strengthSessions),
            workoutRow("Cardio sessions", number(current.workouts().cardioSessionCount()), number(previous.workouts().cardioSessionCount()), number(year.workouts().cardioSessionCount()), "", false, false, false, snapshot.workoutTargets(), WeeklyWorkoutTargets.Period::cardioSessions),
            workoutRow("Stretching sessions", number(current.workouts().stretchingSessionCount()), number(previous.workouts().stretchingSessionCount()), number(year.workouts().stretchingSessionCount()), "", false, false, false, snapshot.workoutTargets(), WeeklyWorkoutTargets.Period::stretchingSessions),
            workoutRow("Active cardio minutes", minutes(number(current.workouts().activeCardioSeconds())), minutes(number(previous.workouts().activeCardioSeconds())), minutes(number(year.workouts().activeCardioSeconds())), "min",
                Boolean.FALSE.equals(current.workouts().cardioDurationComplete()), Boolean.FALSE.equals(previous.workouts().cardioDurationComplete()), Boolean.FALSE.equals(year.workouts().cardioDurationComplete()), snapshot.workoutTargets(), WeeklyWorkoutTargets.Period::activeCardioSeconds)
        );
        var weekly = snapshot.weeklyRoutines() == null ? List.<WeeklyRoutineView>of() : snapshot.weeklyRoutines().stream()
            .map(routine -> new WeeklyRoutineView(routine.name(), routine.completedWeeks() + "/" + routine.eligibleWeeks() + " weeks",
                routine.checkinDates().isEmpty() ? "No check-ins" : routine.checkinDates().stream().map(date -> date.format(SUBJECT_DATE)).collect(java.util.stream.Collectors.joining(", ")))).toList();
        return new VisualSummary(new PeriodHeaders(periodLabel(current), periodLabel(previous), periodLabel(year)), hero, cards, workouts, nutritionTable(snapshot.nutrition()), weekly,
            reflection == null ? null : new ReflectionTeaser(reflection.title(), reflection.summary()),
            goals == null ? "Personal targets unavailable for this report" : "Goals saved with report · " + goals.capturedAt().atZone(DateTimes.USER_ZONE).toLocalDate().format(RANGE_END_DATE));
    }

    private BigDecimal sleepSeconds(Summary period) { return period.sleep() == null ? null : period.sleep().totalSleepSeconds(); }
    private BigDecimal minutes(BigDecimal seconds) { return seconds == null ? null : seconds.divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP); }
    private BigDecimal number(Integer value) { return value == null ? null : BigDecimal.valueOf(value); }
    private String amount(BigDecimal value, String unit, int scale) { return value == null ? UNAVAILABLE : decimal(value, scale) + (unit.isEmpty() ? "" : " " + unit); }
    private Status neutral(String label) { return new Status(label, NEUTRAL_COLOR); }
    private Status goalStatus(BigDecimal value, BigDecimal target, boolean maximum, String met, String missed) {
        if (target == null) return neutral("Target unavailable");
        boolean reached = maximum ? value.compareTo(target) <= 0 : value.compareTo(target) >= 0;
        return new Status(reached ? met : missed, reached ? MET_COLOR : MISSED_COLOR);
    }
    private String bodyFatDetail(BigDecimal fat, WeeklySummarySnapshot.EmailGoals goals) {
        return (fat == null ? "Body fat unavailable" : "Body fat " + fat.stripTrailingZeros().toPlainString() + "%") + " · "
            + (goals == null || goals.inShapeBodyFatLimit() == null ? "Target unavailable" : "Goal ≤ " + goals.inShapeBodyFatLimit().stripTrailingZeros().toPlainString() + "%");
    }
    private Status bodyFatStatus(BigDecimal fat, WeeklySummarySnapshot.EmailGoals goals) {
        if (fat == null) return neutral("Body fat unavailable");
        if (goals == null) return neutral("Target unavailable");
        var rules = goals.rules();
        if (rules != null && rules.bodyFatStages() != null)
            return rules.bodyFatStages().stream().filter(stage -> fat.compareTo(stage.threshold()) > 0)
                .findFirst().map(stage -> new Status(stage.label(), stage.color())).orElse(neutral("Not classified"));
        return goalStatus(fat, goals.inShapeBodyFatLimit(), true, "Body-fat goal met", "Above body-fat goal");
    }
    private Status sleepStatus(AverageSleep sleep, WeeklySummarySnapshot.FixedRules rules) {
        BigDecimal value = sleep == null ? null : sleep.totalSleepSeconds();
        if (value == null) return neutral(UNAVAILABLE);
        if (rules == null) return neutral("Target unavailable");
        if (rules.sleepStages() != null && sleep.statusScore() != null)
            return rules.sleepStages().stream().filter(stage -> BigDecimal.valueOf(sleep.statusScore()).compareTo(stage.threshold()) >= 0)
                .findFirst().map(stage -> new Status(stage.label(), stage.color())).orElseThrow();
        return goalStatus(value, BigDecimal.valueOf(rules.sleepMinimumSeconds()), false, "Sleep goal met", "Below sleep goal");
    }
    private Status moodStatus(BigDecimal value, WeeklySummarySnapshot.FixedRules rules) {
        if (value == null) return neutral(UNAVAILABLE);
        if (rules == null) return neutral("Rule unavailable");
        if (value.compareTo(rules.moodGoodMinimum()) >= 0) return new Status("Good", MET_COLOR);
        return value.compareTo(rules.moodNeutralMinimum()) >= 0 ? neutral("Neutral") : new Status("Low", MISSED_COLOR);
    }
    private Status pressureStatus(BloodPressureMeasurement value, WeeklySummarySnapshot.FixedRules rules) {
        if (value == null) return neutral(UNAVAILABLE);
        if (rules == null) return neutral("Rule unavailable");
        var rule = rules.bloodPressureStages().stream().filter(stage ->
            (stage.systolicAbove() == null && stage.diastolicAbove() == null)
                || (stage.systolicAbove() != null && value.systolic() > stage.systolicAbove())
                || (stage.diastolicAbove() != null && value.diastolic() > stage.diastolicAbove())).findFirst().orElseThrow();
        return new Status(rule.label(), rule.color());
    }
    private Delta unavailableDelta() { return new Delta(UNAVAILABLE, NEUTRAL_COLOR); }
    private Delta visualDelta(BigDecimal current, BigDecimal previous, String unit, int scale, boolean higherBetter) {
        if (current == null || previous == null) return unavailableDelta();
        var difference = current.subtract(previous).setScale(scale, RoundingMode.HALF_UP);
        String change = unit.equals("min") ? (difference.signum() > 0 ? "+" : "") + difference.stripTrailingZeros().toPlainString() : signed(difference, scale);
        return new Delta(change + (unit.isEmpty() ? "" : " " + unit), !higherBetter || difference.signum() == 0 ? NEUTRAL_COLOR : difference.signum() > 0 ? MET_COLOR : MISSED_COLOR);
    }
    private WorkoutRow workoutRow(String label, BigDecimal current, BigDecimal previous, BigDecimal year, String unit,
        boolean currentPartial, boolean previousPartial, boolean yearPartial, WeeklyWorkoutTargets plans,
        java.util.function.Function<WeeklyWorkoutTargets.Period, Integer> measure) {
        BigDecimal target = workoutTarget(plans == null ? null : plans.current(), measure, unit);
        BigDecimal previousTarget = workoutTarget(plans == null ? null : plans.previous(), measure, unit);
        BigDecimal yearTarget = workoutTarget(plans == null ? null : plans.yearAgo(), measure, unit);
        return new WorkoutRow(label, partialAmount(current, unit, currentPartial), partialAmount(previous, unit, previousPartial),
            currentPartial || previousPartial ? unavailableDelta() : visualDelta(current, previous, unit, unit.equals("min") ? 2 : 0, false),
            partialAmount(year, unit, yearPartial), currentPartial || yearPartial ? unavailableDelta() : visualDelta(current, year, unit, unit.equals("min") ? 2 : 0, false),
            workoutTargetLabel(target, unit), workoutStatus(current, target, currentPartial),
            workoutTargetLabel(previousTarget, unit), workoutStatus(previous, previousTarget, previousPartial),
            workoutTargetLabel(yearTarget, unit), workoutStatus(year, yearTarget, yearPartial));
    }
    private BigDecimal workoutTarget(WeeklyWorkoutTargets.Period period,
        java.util.function.Function<WeeklyWorkoutTargets.Period, Integer> measure, String unit) {
        BigDecimal value = period == null ? null : number(measure.apply(period));
        return unit.equals("min") ? minutes(value) : value;
    }
    private String workoutTargetLabel(BigDecimal target, String unit) {
        return target == null ? "Plan unavailable" : "≥ " + amount(target, unit, unit.equals("min") ? 2 : 0);
    }
    private Status workoutStatus(BigDecimal actual, BigDecimal target, boolean partial) {
        return target == null ? neutral("Plan unavailable")
            : assessmentStatus(NutrientAssessment.compare(actual, target, MacroTargetComparison.MINIMUM, !partial));
    }
    private String partialAmount(BigDecimal value, String unit, boolean partial) { return value == null ? UNAVAILABLE : (partial ? "≥ " : "") + (unit.equals("min") ? value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " min" : amount(value, unit, 0)) + (partial ? " · Partial coverage" : ""); }

    private NutritionTable nutritionTable(WeeklyNutritionSnapshot nutrition) {
        var keys = List.of("protein", "carbohydrate", "fat", "vitaminD", "omega3", "fiber", "magnesium");
        var labels = List.of("Protein", "Carbs", "Fat", "Vitamin D", "Total omega-3", "Fiber", "Magnesium");
        var units = List.of("g", "g", "g", "µg", "mg", "g", "mg");
        var rows = new ArrayList<NutrientRow>();
        for (int index = 0; index < keys.size(); index++)
            rows.add(nutrientRow(nutrition, keys.get(index), labels.get(index), units.get(index)));
        var references = new ArrayList<NutrientRow>();
        if (nutrition != null) {
            references.add(nutrientRow(nutrition, "epaDha", "EPA+DHA", "mg"));
            references.add(nutrientRow(nutrition, "ala", "ALA", "mg"));
            var total = nutrientMeasure(nutrition.current(), "omega3");
            if (total != null && total.target() != null)
                references.add(nutrientRow(nutrition, "legacyOmega3", "Legacy total omega-3 goal", "mg"));
        }
        boolean hasYear = nutrition != null && nutrition.yearAgo() != null && nutrition.yearAgo().measures().stream().anyMatch(measure -> measure.average() != null);
        return new NutritionTable(rows, hasYear, nutrition == null ? "Nutrient evidence and targets were not captured in this report." : "Targets captured with report; averages use complete recorded days.", references);
    }
    private NutrientRow nutrientRow(WeeklyNutritionSnapshot nutrition, String key, String label, String unit) {
        String measureKey = key.equals("legacyOmega3") ? "omega3" : key;
        var current = nutrition == null ? null : nutrientMeasure(nutrition.current(), measureKey);
        var previous = nutrition == null ? null : nutrientMeasure(nutrition.previous(), measureKey);
        var year = nutrition == null ? null : nutrientMeasure(nutrition.yearAgo(), measureKey);
        return new NutrientRow(key, label, nutrientAmount(current), nutrientTarget(nutrition, current, key),
            nutrientStatus(nutrition, nutrition == null ? null : nutrition.current(), current, key),
            baselineNutrientAmount(previous), nutrientTarget(nutrition, previous, key), nutrientDelta(current, previous, unit),
            baselineNutrientAmount(year), nutrientTarget(nutrition, year, key), nutrientDelta(current, year, unit),
            current == null ? "No captured nutrient evidence" : (current.eligibleDays() < 7 ? "Partial coverage · " : "") + current.eligibleDays() + "/7 complete days"
                + (current.totalFoods() == 0 ? "" : " · " + current.coveredFoods() + "/" + current.totalFoods() + " foods")
                + (current.estimatedFoods() == 0 ? "" : " · estimates"),
            nutrientStatus(nutrition, nutrition == null ? null : nutrition.previous(), previous, key),
            nutrientStatus(nutrition, nutrition == null ? null : nutrition.yearAgo(), year, key));
    }
    private WeeklyNutritionSnapshot.Measure nutrientMeasure(WeeklyNutritionSnapshot.Period period, String key) {
        return period == null ? null : period.measures().stream().filter(measure -> measure.key().equals(key)).findFirst().orElse(null);
    }
    private String nutrientAmount(WeeklyNutritionSnapshot.Measure measure) {
        return measure == null || measure.average() == null ? UNAVAILABLE : (measure.estimatedFoods() > 0 ? "≈ " : "") + amount(measure.average(), measure.unit(), 2);
    }
    private String baselineNutrientAmount(WeeklyNutritionSnapshot.Measure measure) {
        return nutrientAmount(measure) + (measure == null ? "" : " · " + measure.eligibleDays() + "/7 days" + (measure.eligibleDays() < 7 ? " · Partial coverage" : ""));
    }
    private String nutrientTarget(WeeklyNutritionSnapshot.Measure measure) {
        return measure == null || measure.target() == null || measure.comparison() == null || measure.unavailableReason() != null ? "Target unavailable"
            : (measure.comparison() == MacroTargetComparison.MINIMUM ? "≥ " : "≤ ") + amount(measure.target(), measure.unit(), 2);
    }
    private String nutrientTarget(WeeklyNutritionSnapshot nutrition, WeeklyNutritionSnapshot.Measure measure, String key) {
        if (key.equals("omega3")) return nutrition == null ? "Target unavailable" : "See report references";
        if (key.equals("vitaminD") && nutrition != null && nutrition.targets() != null
            && nutrition.targets().vitaminD().source() == com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSource.EFSA_AI)
            return measure == null || measure.target() == null ? "Reference unavailable" : amount(measure.target(), measure.unit(), 2) + " reference";
        return nutrientTarget(measure);
    }
    private Status assessmentStatus(NutrientAssessment.Result result) { return new Status(result.label(), result.color()); }
    private Status nutrientStatus(WeeklyNutritionSnapshot nutrition, WeeklyNutritionSnapshot.Period period,
        WeeklyNutritionSnapshot.Measure measure, String key) {
        if (key.equals("omega3")) {
            var epa = nutrientMeasure(period, "epaDha"); var ala = nutrientMeasure(period, "ala");
            return assessmentStatus(NutrientAssessment.omega3(epa == null ? null : epa.valueForAssessment(), epa == null ? null : epa.targetForAssessment(), true,
                ala == null ? null : ala.valueForAssessment(), ala == null ? null : ala.targetForAssessment(), true));
        }
        if (measure == null) return neutral(UNAVAILABLE);
        if (key.equals("vitaminD"))
            return assessmentStatus(NutrientAssessment.vitaminD(measure.valueForAssessment(), measure.targetForAssessment(),
                nutrition.targets() == null ? null : nutrition.targets().vitaminD().source(), true));
        if (measure.unavailableReason() != null) return neutral("Target unavailable");
        return assessmentStatus(NutrientAssessment.compare(measure.valueForAssessment(), measure.targetForAssessment(), measure.comparison(), true));
    }
    private Delta nutrientDelta(WeeklyNutritionSnapshot.Measure current, WeeklyNutritionSnapshot.Measure baseline, String unit) {
        return visualDelta(current == null ? null : current.average(), baseline == null ? null : baseline.average(), unit, 2, false);
    }

    private List<CardRow> withWeeklyRoutines(List<CardRow> rows, WeeklySummarySnapshot snapshot) {
        if (snapshot.weeklyRoutines() == null) return rows; // Older immutable snapshots did not capture weekly routines.
        var result = new ArrayList<>(rows);
        snapshot.weeklyRoutines().forEach(routine -> result.add(new CardRow(
            new MetricCard(routine.name(), routine.completedWeeks() + "/" + routine.eligibleWeeks() + " weeks · " + percentage(routine.percentage()),
                "Saturday–Friday · Recorded dates: " + (routine.checkinDates().isEmpty() ? "None" : routine.checkinDates().toString()),
                unknownComparison("last week"), unknownComparison("52 weeks ago")), null, "Weekly routines")));
        return result;
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
        String label = record.subjectLabel() == null ? record.label() : record.label() + " — " + record.subjectLabel();
        return new RecordView(label, recordValue(record.value(), record.unit()), record.date().format(SUBJECT_DATE));
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

    private MetricCard workoutCard(Summary current) {
        WorkoutSummary value = current.workouts();
        return new MetricCard(
            "Workout measurements",
            "Recorded totals",
            String.join(" · ", List.of(
                recordedWorkoutMetric(value.distanceReadingCount(), "Distance", decimal(value.totalDistanceKm(), 1) + " km"),
                recordedWorkoutMetric(value.calorieReadingCount(), "Workout energy", whole(BigDecimal.valueOf(value.totalCalories())) + " kcal"),
                recordedWorkoutMetric(value.strengthSetCount(), "Strength volume", decimal(value.strengthVolumeKg(), 1) + " kg", "sets")
            )),
            new Comparison("Recorded exercise metrics", ComparisonStatus.UNKNOWN),
            new Comparison("Missing measurements remain unknown", ComparisonStatus.UNKNOWN)
        );
    }

    private List<CardRow> rowsWithWorkoutActivity(List<MetricCard> cards, Summary current, Summary previous, Summary yearAgo) {
        List<CardRow> result = rows(cards);
        result.add(new CardRow(
            sessionCard("Strength sessions", current, previous, yearAgo, WorkoutSummary::strengthSessionCount),
            sessionCard("Cardio sessions", current, previous, yearAgo, WorkoutSummary::cardioSessionCount),
            "Workout activity"));
        result.add(new CardRow(
            sessionCard("Stretching sessions", current, previous, yearAgo, WorkoutSummary::stretchingSessionCount),
            new MetricCard("Active cardio minutes", cardioMinutes(current.workouts()),
                "Actual recorded cardio segments · " + periodLabel(current),
                activityComparison(current.workouts().activeCardioSeconds(), previous.workouts().activeCardioSeconds(), true,
                    "last week", previous, current.workouts(), previous.workouts()),
                activityComparison(current.workouts().activeCardioSeconds(), yearAgo.workouts().activeCardioSeconds(), true,
                    "52 weeks ago", yearAgo, current.workouts(), yearAgo.workouts()))));
        return result;
    }

    private MetricCard sessionCard(String label, Summary current, Summary previous, Summary yearAgo, java.util.function.Function<WorkoutSummary, Integer> count) {
        return new MetricCard(label, sessionCount(count.apply(current.workouts())), "Recorded sessions · " + periodLabel(current),
            activityComparison(count.apply(current.workouts()), count.apply(previous.workouts()), false, "last week", previous, current.workouts(), previous.workouts()),
            activityComparison(count.apply(current.workouts()), count.apply(yearAgo.workouts()), false, "52 weeks ago", yearAgo, current.workouts(), yearAgo.workouts()));
    }

    private String periodLabel(Summary period) {
        return period.startDate().format(SUBJECT_DATE) + " – " + period.endDate().format(RANGE_END_DATE);
    }

    private String sessionCount(Integer count) {
        return count == null ? "Not available" : count + (count == 1 ? " session" : " sessions");
    }

    private String cardioMinutes(WorkoutSummary value) {
        return value.activeCardioSeconds() == null ? "Not available" : minutes(value.activeCardioSeconds()) + " min" + partialCardio(value);
    }

    private String partialCardio(WorkoutSummary value) {
        return Boolean.FALSE.equals(value.cardioDurationComplete()) ? " · Partial coverage" : "";
    }

    private String minutes(int seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private Comparison activityComparison(Integer current, Integer baseline, boolean cardio, String label, Summary period, WorkoutSummary currentWorkout, WorkoutSummary baselineWorkout) {
        String value = cardio ? cardioMinutes(baselineWorkout) : sessionCount(baseline);
        String change = current == null || baseline == null ? "Not available" :
            (current > baseline ? "+" : "") + (cardio ? minutes(current - baseline) + " min" : (current - baseline) + (Math.abs(current - baseline) == 1 ? " session" : " sessions"));
        return new Comparison(value + " · " + change + " vs " + label + " · " + periodLabel(period)
            + (cardio && Boolean.FALSE.equals(currentWorkout.cardioDurationComplete()) ? " · Partial coverage" : ""), ComparisonStatus.UNKNOWN);
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
            backPainComparison(summary.episodeCount(), summary.lastWeek(), "last week"),
            backPainComparison(summary.episodeCount(), summary.yearAgo(), "52 weeks ago")
        );
    }

    private Comparison backPainComparison(int currentEpisodes, BackPainPeriodSummary baseline, String label) {
        if (baseline == null || baseline.checkInCount() == 0) return unknownComparison(label);
        return new Comparison(
            currentEpisodes + " episodes vs " + baseline.episodeCount() + " " + label + " · " + baseline.checkInCount() + " check-ins",
            ComparisonStatus.UNKNOWN
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
