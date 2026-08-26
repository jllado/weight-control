package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.BehaviorSubject;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.Source;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.SourceReference;
import com.jllado.weightcontrol.service.PersonalRecordCalculator.Sources;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

final class DerivedPersonalRecordCalculator {

    private DerivedPersonalRecordCalculator() {
    }

    static List<DerivedObservation> calculate(Sources sources) {
        List<DerivedObservation> observations = new ArrayList<>();
        addBodyAndVitalDerivatives(observations, sources);
        addWorkoutSessionTotals(observations, sources.workouts());
        addDashboardDays(observations, sources);
        addCompletedPeriods(observations, sources);
        addRollingPeriods(observations, sources);
        return observations;
    }

    private static void addBodyAndVitalDerivatives(List<DerivedObservation> observations, Sources sources) {
        List<Weight> weights = sorted(sources.weights(), Comparator.comparing(Weight::getMeasuredAt).thenComparing(Weight::getId));
        BigDecimal heightMeters = sources.user() == null || sources.user().getHeightCm() == null
            ? null
            : BigDecimal.valueOf(sources.user().getHeightCm()).movePointLeft(2);
        for (int index = 0; index < weights.size(); index++) {
            Weight current = weights.get(index);
            LocalDate date = DateTimes.toLocalDate(current.getMeasuredAt());
            Source source = directSource(PersonalRecordSourceType.WEIGHT, current.getId());
            if (heightMeters != null) {
                add(observations, PersonalRecordCatalogMetric.BODY_BMI, null, subject("BODY_CHANGE", 1L, "BMI"), current.getWeight().divide(heightMeters.multiply(heightMeters), 2, RoundingMode.HALF_UP), date, source);
            }
            if (index > 0) {
                Weight previous = weights.get(index - 1);
                addChange(observations, PersonalRecordCatalogMetric.CHANGE_KG, "Weight change", current.getWeight(), previous.getWeight(), date, source);
                addChange(observations, PersonalRecordCatalogMetric.CHANGE_KG, "Fat-mass change", current.getFat(), previous.getFat(), date, source);
                addChange(observations, PersonalRecordCatalogMetric.CHANGE_PERCENT, "Fat-percentage change", current.getFatPercentage(), previous.getFatPercentage(), date, source);
                addChange(observations, PersonalRecordCatalogMetric.CHANGE_KG, "Muscle-mass change", current.getMuscle(), previous.getMuscle(), date, source);
                addChange(observations, PersonalRecordCatalogMetric.CHANGE_PERCENT, "Muscle-percentage change", current.getMusclePercentage(), previous.getMusclePercentage(), date, source);
            }
        }

        List<BloodPressure> pressures = sorted(sources.bloodPressures(), Comparator.comparing(BloodPressure::getMeasuredAt).thenComparing(BloodPressure::getId));
        for (int index = 1; index < pressures.size(); index++) {
            BloodPressure current = pressures.get(index);
            BloodPressure previous = pressures.get(index - 1);
            LocalDate date = DateTimes.toLocalDate(current.getMeasuredAt());
            Source source = directSource(PersonalRecordSourceType.BLOOD_PRESSURE, current.getId());
            addChange(observations, PersonalRecordCatalogMetric.CHANGE_MM_HG, "Systolic change", decimal(current.getUpper()), decimal(previous.getUpper()), date, source);
            addChange(observations, PersonalRecordCatalogMetric.CHANGE_MM_HG, "Diastolic change", decimal(current.getLower()), decimal(previous.getLower()), date, source);
        }

        List<LipidPanel> panels = sorted(sources.lipidPanels(), Comparator.comparing(LipidPanel::getPanelDate).thenComparing(LipidPanel::getId));
        for (int index = 1; index < panels.size(); index++) {
            LipidPanel current = panels.get(index);
            LipidPanel previous = panels.get(index - 1);
            Source source = directSource(PersonalRecordSourceType.LIPID_PANEL, current.getId());
            addChange(observations, PersonalRecordCatalogMetric.CHANGE_MG_PER_DL, "Total-cholesterol change", decimal(current.getTotalCholesterol()), decimal(previous.getTotalCholesterol()), current.getPanelDate(), source);
            addChange(observations, PersonalRecordCatalogMetric.CHANGE_MG_PER_DL, "HDL change", decimal(current.getHdlCholesterol()), decimal(previous.getHdlCholesterol()), current.getPanelDate(), source);
            addChange(observations, PersonalRecordCatalogMetric.CHANGE_MG_PER_DL, "LDL change", decimal(current.getLdlCholesterol()), decimal(previous.getLdlCholesterol()), current.getPanelDate(), source);
            addChange(observations, PersonalRecordCatalogMetric.CHANGE_MG_PER_DL, "Triglycerides change", decimal(current.getTriglycerides()), decimal(previous.getTriglycerides()), current.getPanelDate(), source);
        }
    }

    private static void addWorkoutSessionTotals(List<DerivedObservation> observations, List<Workout> workouts) {
        sorted(workouts, Comparator.comparing(Workout::getWorkoutDate).thenComparing(Workout::getId)).forEach(workout -> {
            Source source = derivedSource(Set.of(new SourceReference(PersonalRecordSourceType.WORKOUT, workout.getId())));
            WorkoutAggregate workoutAggregate = aggregate(workout.getLines());
            addWorkoutAggregate(observations, workoutAggregate, null, subject("WORKOUT_TOTAL", null, "Workout session"), workout.getWorkoutDate(), source);
            workout.getLines().stream().collect(java.util.stream.Collectors.groupingBy(WorkoutLine::getExercise, LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .forEach((exercise, lines) -> addWorkoutAggregate(
                    observations,
                    aggregate(lines),
                    exercise,
                    subject("EXERCISE_TOTAL", exercise.getId(), exercise.getName() + " session"),
                    workout.getWorkoutDate(),
                    source
                ));
        });
    }

    private static void addWorkoutAggregate(List<DerivedObservation> observations, WorkoutAggregate aggregate, Exercise exercise, BehaviorSubject subject, LocalDate date, Source source) {
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_SET_COUNT, exercise, subject, aggregate.setCount(), date, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_INTERVAL_COUNT, exercise, subject, aggregate.intervalCount(), date, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_REPETITIONS, exercise, subject, aggregate.repetitions(), date, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_DURATION, exercise, subject, aggregate.durationSeconds(), date, source);
        addOptional(observations, PersonalRecordCatalogMetric.CARDIO_DISTANCE, exercise, subject, aggregate.distanceKm(), date, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_STRENGTH_VOLUME, exercise, subject, aggregate.strengthVolume(), date, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_CALORIES, exercise, subject, aggregate.calories(), date, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_AVERAGE_HEART_RATE, exercise, subject, aggregate.averageHeartRate(), date, source);
    }

    private static void addDashboardDays(List<DerivedObservation> observations, Sources sources) {
        LocalDate completed = completedDate(sources);
        if (completed == null) {
            return;
        }
        sorted(sources.dailyStatuses(), Comparator.comparing(DailyStatus::getStatusDate).thenComparing(DailyStatus::getId)).stream()
            .filter(status -> !status.getStatusDate().isAfter(completed))
            .forEach(status -> {
                Source source = directSource(PersonalRecordSourceType.DAILY_STATUS, status.getId());
                addDashboardCategory(observations, "All routines", 1L, status.getTotalRoutines(), status.getRoutinesDone(), status.getRoutinesPercentage(), status.getRoutinesScore(), status.getRoutinesStatus(), status.getStatusDate(), source);
                addDashboardCategory(observations, "Weight routines", 2L, status.getTotalWeightRoutines(), status.getWeightDone(), status.getWeightPercentage(), status.getWeightScore(), status.getWeightStatus(), status.getStatusDate(), source);
                addDashboardCategory(observations, "Blood-pressure routines", 3L, status.getTotalBloodPressureRoutines(), status.getBloodPressureDone(), status.getBloodPressurePercentage(), status.getBloodPressureScore(), status.getBloodPressureStatus(), status.getStatusDate(), source);
                addDashboardCategory(observations, "Flexibility routines", 4L, status.getTotalFlexibilityRoutines(), status.getFlexibilityDone(), status.getFlexibilityPercentage(), status.getFlexibilityScore(), status.getFlexibilityStatus(), status.getStatusDate(), source);
                addDashboardCategory(observations, "Mind routines", 5L, status.getTotalMindRoutines(), status.getMindDone(), status.getMindPercentage(), status.getMindScore(), status.getMindStatus(), status.getStatusDate(), source);
            });
    }

    private static void addDashboardCategory(List<DerivedObservation> observations, String label, Long id, int total, int done, BigDecimal percentage, BigDecimal score, BigDecimal status, LocalDate date, Source source) {
        BehaviorSubject subject = subject("DASHBOARD", id, label);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_TOTAL_COUNT, null, subject, decimal(total), date, source);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_COMPLETED_COUNT, null, subject, decimal(done), date, source);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_COMPLETION_PERCENTAGE, null, subject, percentage, date, source);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_SCORE, null, subject, score, date, source);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_STATUS, null, subject, status, date, source);
    }

    private static void addCompletedPeriods(List<DerivedObservation> observations, Sources sources) {
        LocalDate completed = completedDate(sources);
        LocalDate first = firstEvidenceDate(sources);
        if (completed == null || first == null) {
            return;
        }
        LocalDate weekStart = DateTimes.startOfDashboardWeek(first);
        while (!weekStart.plusDays(6).isAfter(completed)) {
            addPeriod(observations, sources, weekStart, weekStart.plusDays(6), "Weekly", "WEEKLY");
            weekStart = weekStart.plusWeeks(1);
        }
        YearMonth month = YearMonth.from(first);
        YearMonth lastMonth = YearMonth.from(completed);
        while (!month.isAfter(lastMonth)) {
            LocalDate start = month.atDay(1);
            LocalDate end = month.atEndOfMonth();
            if (!end.isAfter(completed)) {
                addPeriod(observations, sources, start, end, "Monthly", "MONTHLY");
            }
            month = month.plusMonths(1);
        }
    }

    private static void addRollingPeriods(List<DerivedObservation> observations, Sources sources) {
        LocalDate completed = completedDate(sources);
        if (completed == null) {
            return;
        }
        evidenceDates(sources).filter(date -> !date.isAfter(completed)).sorted().distinct().forEach(end -> {
            LocalDate start = end.minusDays(29);
            addPeriod(observations, sources, start, end, "30-day", "ROLLING_30");
            addRollingChanges(observations, sources, end);
        });
    }

    private static void addPeriod(List<DerivedObservation> observations, Sources sources, LocalDate start, LocalDate end, String label, String subjectType) {
        addAverages(observations, sources.weights(), item -> DateTimes.toLocalDate(item.getMeasuredAt()), start, end, subjectType, label, PersonalRecordSourceType.WEIGHT,
            Map.of(
                "body weight", Weight::getWeight,
                "fat mass", Weight::getFat,
                "fat percentage", Weight::getFatPercentage,
                "muscle mass", Weight::getMuscle,
                "muscle percentage", Weight::getMusclePercentage
            ),
            Map.of(
                "body weight", PersonalRecordCatalogMetric.BODY_WEIGHT,
                "fat mass", PersonalRecordCatalogMetric.BODY_FAT_MASS,
                "fat percentage", PersonalRecordCatalogMetric.BODY_FAT_PERCENTAGE,
                "muscle mass", PersonalRecordCatalogMetric.BODY_MUSCLE_MASS,
                "muscle percentage", PersonalRecordCatalogMetric.BODY_MUSCLE_PERCENTAGE
            ));
        addAverages(observations, sources.bloodPressures(), item -> DateTimes.toLocalDate(item.getMeasuredAt()), start, end, subjectType, label, PersonalRecordSourceType.BLOOD_PRESSURE,
            Map.of("systolic pressure", item -> decimal(item.getUpper()), "diastolic pressure", item -> decimal(item.getLower())),
            Map.of("systolic pressure", PersonalRecordCatalogMetric.BLOOD_PRESSURE_SYSTOLIC, "diastolic pressure", PersonalRecordCatalogMetric.BLOOD_PRESSURE_DIASTOLIC));
        addAverages(observations, sources.lipidPanels(), LipidPanel::getPanelDate, start, end, subjectType, label, PersonalRecordSourceType.LIPID_PANEL,
            Map.of("total cholesterol", item -> decimal(item.getTotalCholesterol()), "HDL", item -> decimal(item.getHdlCholesterol()), "LDL", item -> decimal(item.getLdlCholesterol()), "triglycerides", item -> decimal(item.getTriglycerides())),
            Map.of("total cholesterol", PersonalRecordCatalogMetric.LIPID_TOTAL_CHOLESTEROL, "HDL", PersonalRecordCatalogMetric.LIPID_HDL, "LDL", PersonalRecordCatalogMetric.LIPID_LDL, "triglycerides", PersonalRecordCatalogMetric.LIPID_TRIGLYCERIDES));
        addAverages(observations, sources.moods(), Mood::getMoodDate, start, end, subjectType, label, PersonalRecordSourceType.MOOD,
            Map.of("mood", item -> decimal(item.getValue())), Map.of("mood", PersonalRecordCatalogMetric.MOOD));
        addAverages(observations, sources.sleeps(), Sleep::getSleepDate, start, end, subjectType, label, PersonalRecordSourceType.SLEEP,
            sleepValues(), sleepMetrics());
        addNutritionPeriod(observations, sources.user(), sources.meals(), start, end, subjectType, label);
        addWorkoutPeriod(observations, sources.workouts(), start, end, subjectType, label);
        addDashboardPeriod(observations, sources.dailyStatuses(), start, end, subjectType, label);
    }

    private static void addNutritionPeriod(List<DerivedObservation> observations, User user, List<Meal> meals, LocalDate start, LocalDate end, String type, String label) {
        List<Meal> values = inRange(meals, Meal::getMealDate, start, end);
        if (values.isEmpty()) {
            return;
        }
        Map<LocalDate, List<Meal>> byDate = values.stream().collect(java.util.stream.Collectors.groupingBy(Meal::getMealDate));
        Source source = derivedSource(values.stream().map(meal -> new SourceReference(PersonalRecordSourceType.MEAL, meal.getId())).collect(java.util.stream.Collectors.toSet()));
        BigDecimal averageCalories = average(byDate.values().stream().map(day -> day.stream().mapToInt(Meal::getCalories).sum()).map(DerivedPersonalRecordCalculator::decimal).toList());
        add(observations, PersonalRecordCatalogMetric.DAILY_CALORIES, null, subject(type, subjectId("daily calories"), label + " average daily calories"), averageCalories, end, source);
        if (user != null) {
            BigDecimal targetAverage = average(start.datesUntil(end.plusDays(1)).map(date -> decimal(targetCalories(user, date))).toList());
            add(observations, PersonalRecordCatalogMetric.TARGET_DIFFERENCE_KCAL, null, subject(type, subjectId("calorie target difference"), label + " calorie target difference"), averageCalories.subtract(targetAverage), end, source);
        }
        addCompleteMacroPeriod(observations, byDate, Meal::getProteinGrams, PersonalRecordCatalogMetric.DAILY_PROTEIN, "protein", type, label, end, source);
        addCompleteMacroPeriod(observations, byDate, Meal::getCarbohydrateGrams, PersonalRecordCatalogMetric.DAILY_CARBOHYDRATES, "carbohydrates", type, label, end, source);
        addCompleteMacroPeriod(observations, byDate, Meal::getFatGrams, PersonalRecordCatalogMetric.DAILY_FAT, "fat", type, label, end, source);
    }

    private static void addCompleteMacroPeriod(List<DerivedObservation> observations, Map<LocalDate, List<Meal>> byDate, Function<Meal, BigDecimal> getter, PersonalRecordCatalogMetric metric, String name, String type, String label, LocalDate end, Source source) {
        List<BigDecimal> daily = byDate.values().stream().filter(day -> day.stream().allMatch(meal -> getter.apply(meal) != null))
            .map(day -> day.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add)).toList();
        addOptional(observations, metric, null, subject(type, subjectId(name), label + " average daily " + name), average(daily), end, source);
    }

    private static void addWorkoutPeriod(List<DerivedObservation> observations, List<Workout> workouts, LocalDate start, LocalDate end, String type, String label) {
        List<Workout> values = inRange(workouts, Workout::getWorkoutDate, start, end);
        if (values.isEmpty()) {
            return;
        }
        Source source = derivedSource(values.stream().map(workout -> new SourceReference(PersonalRecordSourceType.WORKOUT, workout.getId())).collect(java.util.stream.Collectors.toSet()));
        WorkoutAggregate aggregate = aggregate(values.stream().flatMap(workout -> workout.getLines().stream()).toList());
        BehaviorSubject subject = subject(type, subjectId("workouts"), label + " workout totals");
        add(observations, PersonalRecordCatalogMetric.WORKOUT_SESSION_COUNT, null, subject, decimal(values.size()), end, source);
        add(observations, PersonalRecordCatalogMetric.WORKOUT_SET_COUNT, null, subject, aggregate.setCount(), end, source);
        add(observations, PersonalRecordCatalogMetric.WORKOUT_INTERVAL_COUNT, null, subject, aggregate.intervalCount(), end, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_REPETITIONS, null, subject, aggregate.repetitions(), end, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_DURATION, null, subject, aggregate.durationSeconds(), end, source);
        addOptional(observations, PersonalRecordCatalogMetric.CARDIO_DISTANCE, null, subject, aggregate.distanceKm(), end, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_STRENGTH_VOLUME, null, subject, aggregate.strengthVolume(), end, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_CALORIES, null, subject, aggregate.calories(), end, source);
        addOptional(observations, PersonalRecordCatalogMetric.WORKOUT_AVERAGE_HEART_RATE, null, subject, aggregate.averageHeartRate(), end, source);
    }

    private static void addDashboardPeriod(List<DerivedObservation> observations, List<DailyStatus> statuses, LocalDate start, LocalDate end, String type, String label) {
        List<DailyStatus> values = inRange(statuses, DailyStatus::getStatusDate, start, end);
        if (values.isEmpty()) {
            return;
        }
        Source source = derivedSource(values.stream().map(status -> new SourceReference(PersonalRecordSourceType.DAILY_STATUS, status.getId())).collect(java.util.stream.Collectors.toSet()));
        addDashboardPeriodCategory(observations, values, "All routines", type, label, end, source, DailyStatus::getTotalRoutines, DailyStatus::getRoutinesDone, DailyStatus::getRoutinesPercentage, DailyStatus::getRoutinesScore, DailyStatus::getRoutinesStatus);
        addDashboardPeriodCategory(observations, values, "Weight routines", type, label, end, source, DailyStatus::getTotalWeightRoutines, DailyStatus::getWeightDone, DailyStatus::getWeightPercentage, DailyStatus::getWeightScore, DailyStatus::getWeightStatus);
        addDashboardPeriodCategory(observations, values, "Blood-pressure routines", type, label, end, source, DailyStatus::getTotalBloodPressureRoutines, DailyStatus::getBloodPressureDone, DailyStatus::getBloodPressurePercentage, DailyStatus::getBloodPressureScore, DailyStatus::getBloodPressureStatus);
        addDashboardPeriodCategory(observations, values, "Flexibility routines", type, label, end, source, DailyStatus::getTotalFlexibilityRoutines, DailyStatus::getFlexibilityDone, DailyStatus::getFlexibilityPercentage, DailyStatus::getFlexibilityScore, DailyStatus::getFlexibilityStatus);
        addDashboardPeriodCategory(observations, values, "Mind routines", type, label, end, source, DailyStatus::getTotalMindRoutines, DailyStatus::getMindDone, DailyStatus::getMindPercentage, DailyStatus::getMindScore, DailyStatus::getMindStatus);
    }

    private static void addDashboardPeriodCategory(List<DerivedObservation> observations, List<DailyStatus> values, String category, String type, String label, LocalDate end, Source source, Function<DailyStatus, Integer> total, Function<DailyStatus, Integer> done, Function<DailyStatus, BigDecimal> percentage, Function<DailyStatus, BigDecimal> score, Function<DailyStatus, BigDecimal> status) {
        BehaviorSubject subject = subject(type, subjectId(category), label + " " + category);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_TOTAL_COUNT, null, subject, sum(values.stream().map(total).map(DerivedPersonalRecordCalculator::decimal).toList()), end, source);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_COMPLETED_COUNT, null, subject, sum(values.stream().map(done).map(DerivedPersonalRecordCalculator::decimal).toList()), end, source);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_COMPLETION_PERCENTAGE, null, subject, average(values.stream().map(percentage).toList()), end, source);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_SCORE, null, subject, average(values.stream().map(score).toList()), end, source);
        add(observations, PersonalRecordCatalogMetric.DASHBOARD_STATUS, null, subject, average(values.stream().map(status).toList()), end, source);
    }

    private static void addRollingChanges(List<DerivedObservation> observations, Sources sources, LocalDate end) {
        LocalDate currentStart = end.minusDays(29);
        LocalDate previousEnd = currentStart.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(29);
        addRollingAverageChange(observations, sources.weights(), item -> DateTimes.toLocalDate(item.getMeasuredAt()), Weight::getWeight, PersonalRecordSourceType.WEIGHT, PersonalRecordCatalogMetric.CHANGE_KG, "30-day average weight change", currentStart, end, previousStart, previousEnd);
        addRollingAverageChange(observations, sources.weights(), item -> DateTimes.toLocalDate(item.getMeasuredAt()), Weight::getFatPercentage, PersonalRecordSourceType.WEIGHT, PersonalRecordCatalogMetric.CHANGE_PERCENT, "30-day average fat-percentage change", currentStart, end, previousStart, previousEnd);
        addRollingAverageChange(observations, sources.weights(), item -> DateTimes.toLocalDate(item.getMeasuredAt()), Weight::getMusclePercentage, PersonalRecordSourceType.WEIGHT, PersonalRecordCatalogMetric.CHANGE_PERCENT, "30-day average muscle-percentage change", currentStart, end, previousStart, previousEnd);
        addRollingAverageChange(observations, sources.bloodPressures(), item -> DateTimes.toLocalDate(item.getMeasuredAt()), item -> decimal(item.getUpper()), PersonalRecordSourceType.BLOOD_PRESSURE, PersonalRecordCatalogMetric.CHANGE_MM_HG, "30-day average systolic change", currentStart, end, previousStart, previousEnd);
        addRollingAverageChange(observations, sources.bloodPressures(), item -> DateTimes.toLocalDate(item.getMeasuredAt()), item -> decimal(item.getLower()), PersonalRecordSourceType.BLOOD_PRESSURE, PersonalRecordCatalogMetric.CHANGE_MM_HG, "30-day average diastolic change", currentStart, end, previousStart, previousEnd);
        addRollingAverageChange(observations, sources.moods(), Mood::getMoodDate, item -> decimal(item.getValue()), PersonalRecordSourceType.MOOD, PersonalRecordCatalogMetric.RECOVERY_CHANGE_SCORE, "30-day average mood change", currentStart, end, previousStart, previousEnd);
        addRollingAverageChange(observations, sources.sleeps(), Sleep::getSleepDate, item -> decimal(item.getTotalSleepDuration()), PersonalRecordSourceType.SLEEP, PersonalRecordCatalogMetric.CHANGE_SECONDS, "30-day average sleep-duration change", currentStart, end, previousStart, previousEnd);
        addRollingAverageChange(observations, sources.sleeps(), Sleep::getSleepDate, DerivedPersonalRecordCalculator::positiveSleepHeartRate, PersonalRecordSourceType.SLEEP, PersonalRecordCatalogMetric.CHANGE_BPM, "30-day average sleep-heart-rate change", currentStart, end, previousStart, previousEnd);
        addRollingAverageChange(observations, sources.sleeps(), Sleep::getSleepDate, item -> decimal(item.getAverageHrv()), PersonalRecordSourceType.SLEEP, PersonalRecordCatalogMetric.CHANGE_MILLISECONDS, "30-day average HRV change", currentStart, end, previousStart, previousEnd);
        Map<LocalDate, Integer> calories = sources.meals().stream().collect(java.util.stream.Collectors.groupingBy(Meal::getMealDate, java.util.stream.Collectors.summingInt(Meal::getCalories)));
        addRollingMapChange(observations, calories, PersonalRecordCatalogMetric.CHANGE_KCAL, "30-day average calorie change", currentStart, end, previousStart, previousEnd,
            sources.meals().stream().filter(meal -> inRange(meal.getMealDate(), currentStart, end)).map(meal -> new SourceReference(PersonalRecordSourceType.MEAL, meal.getId())).collect(java.util.stream.Collectors.toSet()));
        addRollingWorkoutChanges(observations, sources.workouts(), currentStart, end, previousStart, previousEnd);
        addRollingDashboardChanges(observations, sources.dailyStatuses(), currentStart, end, previousStart, previousEnd);
    }

    private static void addRollingWorkoutChanges(List<DerivedObservation> observations, List<Workout> workouts, LocalDate currentStart, LocalDate currentEnd, LocalDate previousStart, LocalDate previousEnd) {
        List<Workout> current = inRange(workouts, Workout::getWorkoutDate, currentStart, currentEnd);
        List<Workout> previous = inRange(workouts, Workout::getWorkoutDate, previousStart, previousEnd);
        if (current.isEmpty() || previous.isEmpty()) {
            return;
        }
        WorkoutAggregate currentAggregate = aggregate(current.stream().flatMap(workout -> workout.getLines().stream()).toList());
        WorkoutAggregate previousAggregate = aggregate(previous.stream().flatMap(workout -> workout.getLines().stream()).toList());
        Source source = derivedSource(current.stream().map(workout -> new SourceReference(PersonalRecordSourceType.WORKOUT, workout.getId())).collect(java.util.stream.Collectors.toSet()));
        addDifference(observations, PersonalRecordCatalogMetric.WORKOUT_CHANGE_COUNT, "30-day workout-count change", decimal(current.size()), decimal(previous.size()), currentEnd, source);
        addDifference(observations, PersonalRecordCatalogMetric.WORKOUT_CHANGE_COUNT, "30-day set-count change", currentAggregate.setCount(), previousAggregate.setCount(), currentEnd, source);
        addDifference(observations, PersonalRecordCatalogMetric.WORKOUT_CHANGE_COUNT, "30-day interval-count change", currentAggregate.intervalCount(), previousAggregate.intervalCount(), currentEnd, source);
        addDifference(observations, PersonalRecordCatalogMetric.WORKOUT_CHANGE_SECONDS, "30-day workout-duration change", currentAggregate.durationSeconds(), previousAggregate.durationSeconds(), currentEnd, source);
        addDifference(observations, PersonalRecordCatalogMetric.CHANGE_KM, "30-day workout-distance change", currentAggregate.distanceKm(), previousAggregate.distanceKm(), currentEnd, source);
        addDifference(observations, PersonalRecordCatalogMetric.CHANGE_KG_REPETITIONS, "30-day strength-volume change", currentAggregate.strengthVolume(), previousAggregate.strengthVolume(), currentEnd, source);
        addDifference(observations, PersonalRecordCatalogMetric.WORKOUT_CHANGE_KCAL, "30-day workout-calorie change", currentAggregate.calories(), previousAggregate.calories(), currentEnd, source);
        addDifference(observations, PersonalRecordCatalogMetric.WORKOUT_CHANGE_BPM, "30-day workout-heart-rate change", currentAggregate.averageHeartRate(), previousAggregate.averageHeartRate(), currentEnd, source);
    }

    private static void addRollingDashboardChanges(List<DerivedObservation> observations, List<DailyStatus> statuses, LocalDate currentStart, LocalDate currentEnd, LocalDate previousStart, LocalDate previousEnd) {
        List<DailyStatus> current = inRange(statuses, DailyStatus::getStatusDate, currentStart, currentEnd);
        List<DailyStatus> previous = inRange(statuses, DailyStatus::getStatusDate, previousStart, previousEnd);
        if (current.isEmpty() || previous.isEmpty()) {
            return;
        }
        Source source = derivedSource(current.stream().map(status -> new SourceReference(PersonalRecordSourceType.DAILY_STATUS, status.getId())).collect(java.util.stream.Collectors.toSet()));
        addDifference(observations, PersonalRecordCatalogMetric.BEHAVIOR_CHANGE_PERCENT, "30-day routine-completion change", average(current.stream().map(DailyStatus::getRoutinesPercentage).toList()), average(previous.stream().map(DailyStatus::getRoutinesPercentage).toList()), currentEnd, source);
        addDifference(observations, PersonalRecordCatalogMetric.CHANGE_SCORE, "30-day routine-score change", average(current.stream().map(DailyStatus::getRoutinesScore).toList()), average(previous.stream().map(DailyStatus::getRoutinesScore).toList()), currentEnd, source);
        addDifference(observations, PersonalRecordCatalogMetric.BEHAVIOR_CHANGE_PERCENT, "30-day routine-status change", average(current.stream().map(DailyStatus::getRoutinesStatus).toList()), average(previous.stream().map(DailyStatus::getRoutinesStatus).toList()), currentEnd, source);
    }

    private static void addDifference(List<DerivedObservation> observations, PersonalRecordCatalogMetric metric, String label, BigDecimal current, BigDecimal previous, LocalDate date, Source source) {
        if (current != null && previous != null) {
            add(observations, metric, null, subject("ROLLING_30", null, label), current.subtract(previous), date, source);
        }
    }

    private static <T> void addRollingAverageChange(List<DerivedObservation> observations, List<T> values, Function<T, LocalDate> date, Function<T, BigDecimal> number, PersonalRecordSourceType sourceType, PersonalRecordCatalogMetric metric, String label, LocalDate currentStart, LocalDate currentEnd, LocalDate previousStart, LocalDate previousEnd) {
        List<T> current = inRange(values, date, currentStart, currentEnd);
        List<T> previous = inRange(values, date, previousStart, previousEnd);
        BigDecimal currentAverage = average(current.stream().map(number).filter(Objects::nonNull).toList());
        BigDecimal previousAverage = average(previous.stream().map(number).filter(Objects::nonNull).toList());
        if (currentAverage == null || previousAverage == null) {
            return;
        }
        Set<SourceReference> contributors = current.stream().map(value -> sourceReference(sourceType, value)).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        add(observations, metric, null, subject("ROLLING_30", subjectId(label), label), currentAverage.subtract(previousAverage), currentEnd, derivedSource(contributors));
    }

    private static void addRollingMapChange(List<DerivedObservation> observations, Map<LocalDate, Integer> values, PersonalRecordCatalogMetric metric, String label, LocalDate currentStart, LocalDate currentEnd, LocalDate previousStart, LocalDate previousEnd, Set<SourceReference> contributors) {
        BigDecimal current = average(values.entrySet().stream().filter(entry -> inRange(entry.getKey(), currentStart, currentEnd)).map(entry -> decimal(entry.getValue())).toList());
        BigDecimal previous = average(values.entrySet().stream().filter(entry -> inRange(entry.getKey(), previousStart, previousEnd)).map(entry -> decimal(entry.getValue())).toList());
        if (current != null && previous != null) {
            add(observations, metric, null, subject("ROLLING_30", subjectId(label), label), current.subtract(previous), currentEnd, derivedSource(contributors));
        }
    }

    private static <T> void addAverages(List<DerivedObservation> observations, List<T> sourceValues, Function<T, LocalDate> date, LocalDate start, LocalDate end, String type, String periodLabel, PersonalRecordSourceType sourceType, Map<String, Function<T, BigDecimal>> values, Map<String, PersonalRecordCatalogMetric> metrics) {
        List<T> inPeriod = inRange(sourceValues, date, start, end);
        if (inPeriod.isEmpty()) {
            return;
        }
        Set<SourceReference> contributors = inPeriod.stream().map(value -> sourceReference(sourceType, value)).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Source source = derivedSource(contributors);
        values.forEach((name, getter) -> addOptional(observations, metrics.get(name), null, subject(type, subjectId(name), periodLabel + " average " + name), average(inPeriod.stream().map(getter).filter(Objects::nonNull).toList()), end, source));
    }

    private static Map<String, Function<Sleep, BigDecimal>> sleepValues() {
        return Map.of(
            "total sleep", item -> decimal(item.getTotalSleepDuration()),
            "deep sleep", item -> decimal(item.getDeepSleepDuration()),
            "REM sleep", item -> decimal(item.getRemSleepDuration()),
            "light sleep", item -> decimal(item.getLightSleepDuration()),
            "awake time", item -> decimal(item.getAwakeTime()),
            "sleep heart rate", DerivedPersonalRecordCalculator::positiveSleepHeartRate,
            "HRV", item -> decimal(item.getAverageHrv())
        );
    }

    private static Map<String, PersonalRecordCatalogMetric> sleepMetrics() {
        return Map.of(
            "total sleep", PersonalRecordCatalogMetric.SLEEP_TOTAL_DURATION,
            "deep sleep", PersonalRecordCatalogMetric.SLEEP_DEEP_DURATION,
            "REM sleep", PersonalRecordCatalogMetric.SLEEP_REM_DURATION,
            "light sleep", PersonalRecordCatalogMetric.SLEEP_LIGHT_DURATION,
            "awake time", PersonalRecordCatalogMetric.SLEEP_AWAKE_TIME,
            "sleep heart rate", PersonalRecordCatalogMetric.SLEEP_AVERAGE_HEART_RATE,
            "HRV", PersonalRecordCatalogMetric.SLEEP_AVERAGE_HRV
        );
    }

    private static BigDecimal positiveSleepHeartRate(Sleep sleep) {
        return sleep.getAverageHeartRate() != null && sleep.getAverageHeartRate().signum() > 0 ? sleep.getAverageHeartRate() : null;
    }

    private static WorkoutAggregate aggregate(List<WorkoutLine> lines) {
        lines = lines.stream().filter(line -> line.getExercise().getExerciseType() != ExerciseType.WARM_UP).toList();
        List<WorkoutSegment> segments = lines.stream().flatMap(line -> line.getSegments().stream()).toList();
        long sets = segments.stream().filter(segment -> segment.getWorkoutLine().getExercise().getTrackingMode() != ExerciseTrackingMode.CARDIO).count();
        long intervals = segments.stream().filter(segment -> segment.getWorkoutLine().getExercise().getTrackingMode() == ExerciseTrackingMode.CARDIO).count();
        List<BigDecimal> repetitions = segments.stream().map(WorkoutSegment::getRepetitions).filter(Objects::nonNull).map(DerivedPersonalRecordCalculator::decimal).toList();
        List<BigDecimal> durations = segments.stream().map(WorkoutSegment::getDurationSeconds).filter(Objects::nonNull).map(DerivedPersonalRecordCalculator::decimal).toList();
        List<BigDecimal> distances = segments.stream().map(WorkoutSegment::getDistanceKm).filter(Objects::nonNull).toList();
        List<BigDecimal> volumes = segments.stream().filter(segment -> segment.getWeight() != null && segment.getRepetitions() != null)
            .map(segment -> segment.getWeight().multiply(decimal(segment.getRepetitions()))).toList();
        List<BigDecimal> calories = lines.stream().map(WorkoutLine::getCalories).filter(Objects::nonNull).map(DerivedPersonalRecordCalculator::decimal).toList();
        List<BigDecimal> heartRates = lines.stream().map(WorkoutLine::getAverageHeartRate).filter(Objects::nonNull).map(DerivedPersonalRecordCalculator::decimal).toList();
        return new WorkoutAggregate(decimal(sets), decimal(intervals), sumOrNull(repetitions), sumOrNull(durations), sumOrNull(distances), sumOrNull(volumes), sumOrNull(calories), average(heartRates));
    }

    private static LocalDate completedDate(Sources sources) {
        return sources.user() == null ? null : sources.user().getLastCompletedDashboardDate();
    }

    private static int targetCalories(User user, LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> user.getTypicalCaloriesSaturday();
            case SUNDAY -> user.getTypicalCaloriesSunday();
            case MONDAY -> user.getTypicalCaloriesMonday();
            case TUESDAY -> user.getTypicalCaloriesTuesday();
            case WEDNESDAY -> user.getTypicalCaloriesWednesday();
            case THURSDAY -> user.getTypicalCaloriesThursday();
            case FRIDAY -> user.getTypicalCaloriesFriday();
        };
    }

    private static LocalDate firstEvidenceDate(Sources sources) {
        return evidenceDates(sources).min(LocalDate::compareTo).orElse(null);
    }

    private static Stream<LocalDate> evidenceDates(Sources sources) {
        return Stream.of(
            sources.weights().stream().map(item -> DateTimes.toLocalDate(item.getMeasuredAt())),
            sources.workouts().stream().map(Workout::getWorkoutDate),
            sources.bloodPressures().stream().map(item -> DateTimes.toLocalDate(item.getMeasuredAt())),
            sources.lipidPanels().stream().map(LipidPanel::getPanelDate),
            sources.moods().stream().map(Mood::getMoodDate),
            sources.sleeps().stream().map(Sleep::getSleepDate),
            sources.meals().stream().map(Meal::getMealDate),
            sources.dailyStatuses().stream().map(DailyStatus::getStatusDate)
        ).flatMap(Function.identity());
    }

    private static SourceReference sourceReference(PersonalRecordSourceType type, Object value) {
        Long id = switch (value) {
            case Weight item -> item.getId();
            case Workout item -> item.getId();
            case BloodPressure item -> item.getId();
            case LipidPanel item -> item.getId();
            case Mood item -> item.getId();
            case Sleep item -> item.getId();
            case Meal item -> item.getId();
            case DailyStatus item -> item.getId();
            default -> null;
        };
        return id == null ? null : new SourceReference(type, id);
    }

    private static Source directSource(PersonalRecordSourceType type, Long id) {
        return new Source(type, id, null, null);
    }

    private static Source derivedSource(Set<SourceReference> contributors) {
        return new Source(PersonalRecordSourceType.DERIVED_PERIOD, null, null, null, Set.copyOf(contributors));
    }

    private static void addChange(List<DerivedObservation> observations, PersonalRecordCatalogMetric metric, String label, BigDecimal current, BigDecimal previous, LocalDate date, Source source) {
        add(observations, metric, null, subject(metric.getDomain() == PersonalRecordDomain.VITALS ? "VITAL_CHANGE" : "BODY_CHANGE", subjectId(label), label), current.subtract(previous), date, source);
    }

    private static void add(List<DerivedObservation> observations, PersonalRecordCatalogMetric metric, Exercise exercise, BehaviorSubject subject, BigDecimal value, LocalDate date, Source source) {
        observations.add(new DerivedObservation(metric, exercise, subject, value.setScale(metric.getPrecision(), RoundingMode.HALF_UP), date, source));
    }

    private static void addOptional(List<DerivedObservation> observations, PersonalRecordCatalogMetric metric, Exercise exercise, BehaviorSubject subject, BigDecimal value, LocalDate date, Source source) {
        if (value != null) {
            add(observations, metric, exercise, subject, value, date, source);
        }
    }

    private static BehaviorSubject subject(String type, Long id, String label) {
        return new BehaviorSubject(type, id, label);
    }

    private static Long subjectId(String value) {
        return null;
    }

    private static BigDecimal decimal(long value) {
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        return values.isEmpty() ? null : sum(values).divide(decimal(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumOrNull(List<BigDecimal> values) {
        return values.isEmpty() ? null : sum(values);
    }

    private static <T> List<T> inRange(List<T> values, Function<T, LocalDate> date, LocalDate start, LocalDate end) {
        return values.stream().filter(value -> inRange(date.apply(value), start, end)).toList();
    }

    private static boolean inRange(LocalDate value, LocalDate start, LocalDate end) {
        return !value.isBefore(start) && !value.isAfter(end);
    }

    private static <T> List<T> sorted(List<T> values, Comparator<T> comparator) {
        return values.stream().sorted(comparator).toList();
    }

    record DerivedObservation(PersonalRecordCatalogMetric metric, Exercise exercise, BehaviorSubject subject, BigDecimal value, LocalDate date, Source source) {
    }

    private record WorkoutAggregate(BigDecimal setCount, BigDecimal intervalCount, BigDecimal repetitions, BigDecimal durationSeconds, BigDecimal distanceKm, BigDecimal strengthVolume, BigDecimal calories, BigDecimal averageHeartRate) {
    }
}
