package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PersonalRecordCalculatorTest {

    private final PersonalRecordCalculator calculator = new PersonalRecordCalculator();

    @Test
    void routineCurrentRecordIsExactWhileHistoryContainsOnlyStreakMilestones() {
        Routine routine = new Routine();
        routine.setId(30L);
        routine.setName("Walk");
        OffsetDateTime firstDate = OffsetDateTime.parse("2026-01-01T08:00:00+01:00");
        List<RoutineCheckin> checkins = IntStream.rangeClosed(1, 82)
            .mapToObj(day -> routineCheckin((long) day, routine, firstDate.plusDays(day - 1).toString()))
            .toList();

        var result = calculator.calculateRoutines(List.of(new PersonalRecordCalculator.RoutineSource(routine, checkins)), Map.of());

        assertCurrent(result, PersonalRecordMetric.ROUTINE_BEST_STREAK_MAXIMUM, null, null, "82");
        assertEquals(List.of(new BigDecimal("60"), new BigDecimal("21")), result.history().stream().map(PersonalRecordCalculator.HistoryEvent::value).toList());
    }

    @Test
    void calculatesBodyStrengthTimedAndCardioProgression() {
        Weight firstWeight = weight(1L, "2026-08-01T08:00:00+02:00", "80", "16", "20", "64", "80");
        Weight secondWeight = weight(2L, "2026-08-08T08:00:00+02:00", "79", "15", "19", "65", "82");
        Weight tiedWeight = weight(3L, "2026-08-15T08:00:00+02:00", "79", "15", "19", "65", "82");

        Exercise squat = exercise(1L, "Squat", ExerciseTrackingMode.REPS);
        Exercise plank = exercise(2L, "Plank", ExerciseTrackingMode.SECONDS);
        Exercise running = exercise(3L, "Running", ExerciseTrackingMode.CARDIO);
        Workout firstWorkout = workout(1L, "2026-08-02",
            line(0, squat, segment(0, 10, null, null, null, null, null, null), segment(1, 5, null, "20.004", null, null, null, null)),
            line(1, plank, segment(0, null, 60, null, null, null, null, null)),
            line(2, running, segment(0, null, 600, null, "8", "2", "1", 0))
        );
        Workout secondWorkout = workout(2L, "2026-08-09",
            line(0, squat, segment(0, 12, null, "0", null, null, null, null), segment(1, 6, null, "20", null, null, null, null)),
            line(1, plank, segment(0, null, 75, "0", null, null, null, null)),
            line(2, running, segment(0, null, 720, null, "9", "2.5", "2", 3))
        );

        var result = calculator.calculate(List.of(tiedWeight, secondWeight, firstWeight), List.of(secondWorkout, firstWorkout));

        assertCurrent(result, PersonalRecordMetric.BODY_WEIGHT, null, null, "79.00");
        assertCurrent(result, PersonalRecordMetric.BODY_FAT_MASS, null, null, "15.00");
        assertCurrent(result, PersonalRecordMetric.BODY_MUSCLE_MASS, null, null, "65.00");
        assertCurrent(result, PersonalRecordMetric.WORKOUT_HEAVIEST_LOAD, squat, null, "20.00");
        assertCurrent(result, PersonalRecordMetric.WORKOUT_REPETITIONS, squat, "0.00", "12");
        assertCurrent(result, PersonalRecordMetric.WORKOUT_REPETITIONS, squat, "20.00", "6");
        assertCurrent(result, PersonalRecordMetric.WORKOUT_DURATION, plank, "0.00", "75");
        assertCurrent(result, PersonalRecordMetric.CARDIO_DURATION, running, null, "720");
        assertCurrent(result, PersonalRecordMetric.CARDIO_SPEED, running, null, "9");
        assertCurrent(result, PersonalRecordMetric.CARDIO_DISTANCE, running, null, "2.5");
        assertCurrent(result, PersonalRecordMetric.CARDIO_INCLINE, running, null, "2");
        assertCurrent(result, PersonalRecordMetric.CARDIO_RESISTANCE, running, null, "3");

        var bodyWeightEvents = result.history().stream().filter(event -> event.series().metric() == PersonalRecordMetric.BODY_WEIGHT).toList();
        assertEquals(List.of(PersonalRecordEventKind.TIED, PersonalRecordEventKind.IMPROVED, PersonalRecordEventKind.FIRST), bodyWeightEvents.stream().map(PersonalRecordCalculator.HistoryEvent::kind).toList());
        assertTrue(bodyWeightEvents.get(0).currentRecord());
    }

    @Test
    void recalculatesProgressionAfterEditingAndDeletingSources() {
        Exercise squat = exercise(1L, "Squat", ExerciseTrackingMode.REPS);
        Workout first = workout(1L, "2026-08-01", line(0, squat, segment(0, 5, null, "20", null, null, null, null)));
        Workout edited = workout(2L, "2026-08-08", line(0, squat, segment(0, 10, null, "20", null, null, null, null)));
        Workout later = workout(3L, "2026-08-15", line(0, squat, segment(0, 8, null, "20", null, null, null, null)));

        var original = calculator.calculate(List.of(), List.of(first, edited, later));
        assertCurrent(original, PersonalRecordMetric.WORKOUT_REPETITIONS, squat, "20.00", "10");

        edited.getLines().getFirst().getSegments().getFirst().setRepetitions(7);
        var afterEdit = calculator.calculate(List.of(), List.of(first, edited, later));
        assertCurrent(afterEdit, PersonalRecordMetric.WORKOUT_REPETITIONS, squat, "20.00", "8");
        assertEquals(3L, afterEdit.current().stream()
            .filter(record -> record.series().metric() == PersonalRecordMetric.WORKOUT_REPETITIONS)
            .filter(record -> record.series().loadKg() != null && record.series().loadKg().compareTo(new BigDecimal("20")) == 0)
            .findFirst().orElseThrow().source().id());

        var afterDelete = calculator.calculate(List.of(), List.of(first, edited));
        assertCurrent(afterDelete, PersonalRecordMetric.WORKOUT_REPETITIONS, squat, "20.00", "7");
    }

    @Test
    void calculatesDirectHealthAndCompleteDailyNutritionRecords() {
        BloodPressure pressure = new BloodPressure();
        pressure.setId(10L); pressure.setMeasuredAt(OffsetDateTime.parse("2026-08-01T08:00:00+02:00")); pressure.setUpper(120); pressure.setLower(75);
        LipidPanel lipids = new LipidPanel();
        lipids.setId(11L); lipids.setPanelDate(LocalDate.parse("2026-08-02")); lipids.setTotalCholesterol(180); lipids.setHdlCholesterol(60); lipids.setLdlCholesterol(100); lipids.setTriglycerides(90);
        Mood mood = new Mood();
        mood.setId(12L); mood.setMoodDate(LocalDate.parse("2026-08-03")); mood.setPeriod(MoodPeriod.MORNING); mood.setValue(5);
        Sleep sleep = new Sleep();
        sleep.setId(13L); sleep.setSleepDate(LocalDate.parse("2026-08-04")); sleep.setTotalSleepDuration(28800); sleep.setDeepSleepDuration(5000); sleep.setRemSleepDuration(6000); sleep.setLightSleepDuration(17000); sleep.setAwakeTime(800); sleep.setAverageHeartRate(new BigDecimal("48.5")); sleep.setAverageHrv(70);
        Meal breakfast = meal(14L, "2026-08-05", MealType.BREAKFAST, 0, "0", "10", "5");
        Meal lunch = meal(15L, "2026-08-05", MealType.LUNCH, 600, "40", null, "20");

        var result = calculator.calculate(new PersonalRecordCalculator.Sources(List.of(), List.of(), List.of(pressure), List.of(lipids), List.of(mood), List.of(sleep), List.of(breakfast, lunch)));

        assertCurrent(result, PersonalRecordMetric.BLOOD_PRESSURE_SYSTOLIC_MINIMUM, null, null, "120");
        assertCurrent(result, PersonalRecordMetric.BLOOD_PRESSURE_SYSTOLIC_MAXIMUM, null, null, "120");
        assertCurrent(result, PersonalRecordMetric.LIPID_HDL_MAXIMUM, null, null, "60");
        assertCurrent(result, PersonalRecordMetric.MOOD_MAXIMUM, null, null, "5");
        assertCurrent(result, PersonalRecordMetric.SLEEP_AVERAGE_HEART_RATE_MINIMUM, null, null, "48.5");
        assertCurrent(result, PersonalRecordMetric.MEAL_CALORIES_MINIMUM, null, null, "0");
        assertCurrent(result, PersonalRecordMetric.DAILY_CALORIES_MAXIMUM, null, null, "600");
        assertCurrent(result, PersonalRecordMetric.DAILY_PROTEIN_MAXIMUM, null, null, "40");
        assertCurrent(result, PersonalRecordMetric.DAILY_FAT_MAXIMUM, null, null, "25");
        assertTrue(result.current().stream().noneMatch(record -> record.series().metric() == PersonalRecordMetric.DAILY_CARBOHYDRATES_MAXIMUM));
    }

    @Test
    void treatsZeroSleepHeartRateAsMissing() {
        Sleep zero = new Sleep();
        zero.setId(13L); zero.setSleepDate(LocalDate.parse("2026-08-01")); zero.setAverageHeartRate(BigDecimal.ZERO);
        Sleep positive = new Sleep();
        positive.setId(14L); positive.setSleepDate(LocalDate.parse("2026-08-02")); positive.setAverageHeartRate(new BigDecimal("48.5"));

        var result = calculator.calculate(new PersonalRecordCalculator.Sources(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(zero, positive), List.of()));

        assertCurrent(result, PersonalRecordMetric.SLEEP_AVERAGE_HEART_RATE_MINIMUM, null, null, "48.5");
    }

    @Test
    void omitsSleepHeartRateRecordWhenAllValuesAreZero() {
        Sleep zero = new Sleep();
        zero.setId(13L); zero.setSleepDate(LocalDate.parse("2026-08-01")); zero.setAverageHeartRate(BigDecimal.ZERO);

        var result = calculator.calculate(new PersonalRecordCalculator.Sources(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(zero), List.of()));

        assertTrue(result.current().stream().noneMatch(record -> record.series().metric() == PersonalRecordMetric.SLEEP_AVERAGE_HEART_RATE_MINIMUM));
    }

    @Test
    void appliesDisabledMinimumMaximumAndBothModes() {
        var sources = new PersonalRecordCalculator.Sources(List.of(
            weight(1L, "2026-08-01T08:00:00+02:00", "80", "16", "20", "64", "80"),
            weight(2L, "2026-08-08T08:00:00+02:00", "79", "15", "19", "65", "82")
        ), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        var result = calculator.calculate(sources, Map.of(
            PersonalRecordCatalogMetric.BODY_WEIGHT, PersonalRecordMode.BOTH,
            PersonalRecordCatalogMetric.BODY_FAT_MASS, PersonalRecordMode.DISABLED,
            PersonalRecordCatalogMetric.BODY_MUSCLE_MASS, PersonalRecordMode.MINIMUM
        ));

        assertCurrent(result, PersonalRecordMetric.BODY_WEIGHT, null, null, "79");
        assertCurrent(result, PersonalRecordMetric.BODY_WEIGHT_MAXIMUM, null, null, "80");
        assertCurrent(result, PersonalRecordMetric.BODY_MUSCLE_MASS_MINIMUM, null, null, "64");
        assertTrue(result.current().stream().noneMatch(record -> record.series().metric().getCatalogMetric() == PersonalRecordCatalogMetric.BODY_FAT_MASS));
    }

    @Test
    void calculatesBehaviorBaselinesCheckinsDecisionRatesAndStreaks() {
        User user = new User();
        user.setId(1L);
        Habit habit = new Habit();
        habit.setId(10L); habit.setUser(user); habit.setName("Read");
        HabitBaseline baseline = new HabitBaseline();
        baseline.setId(20L); baseline.setHabit(habit); baseline.setCompletionTotal(4); baseline.setCurrentStreak(2); baseline.setBestStreak(3); baseline.setLastDate(null);
        HabitCheckin habitFirst = habitCheckin(21L, habit, "2026-08-20");
        HabitCheckin habitSecond = habitCheckin(22L, habit, "2026-08-21");

        Routine routine = new Routine();
        routine.setId(30L); routine.setUser(user); routine.setName("Walk");
        RoutineCheckin routineFirst = routineCheckin(31L, routine, "2026-08-20T08:00:00+02:00");
        RoutineCheckin routineSecond = routineCheckin(32L, routine, "2026-08-21T08:00:00+02:00");

        DecisionOutcome miss = decision(40L, user, "2026-08-20", DecisionOutcomeType.MISS);
        DecisionOutcome win = decision(41L, user, "2026-08-21", DecisionOutcomeType.WIN);
        DecisionOutcome secondWin = decision(42L, user, "2026-08-22", DecisionOutcomeType.WIN);

        var result = calculator.calculate(new PersonalRecordCalculator.Sources(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(new PersonalRecordCalculator.HabitSource(habit, baseline, List.of(habitSecond, habitFirst))),
            List.of(new PersonalRecordCalculator.RoutineSource(routine, List.of(routineSecond, routineFirst))),
            List.of(secondWin, miss, win)
        ));

        assertCurrent(result, PersonalRecordMetric.HABIT_COMPLETION_TOTAL_MAXIMUM, null, null, "6");
        assertCurrent(result, PersonalRecordMetric.HABIT_BEST_STREAK_MAXIMUM, null, null, "3");
        assertCurrent(result, PersonalRecordMetric.ROUTINE_BEST_STREAK_MAXIMUM, null, null, "2");
        assertCurrent(result, PersonalRecordMetric.DECISION_TOTAL_MAXIMUM, null, null, "3");
        assertCurrent(result, PersonalRecordMetric.DECISION_WIN_RATE_MAXIMUM, null, null, "66.67");
        assertCurrent(result, PersonalRecordMetric.DECISION_WIN_STREAK_MAXIMUM, null, null, "2");
        assertTrue(result.history().stream().anyMatch(event -> event.source().type() == PersonalRecordSourceType.HABIT_BASELINE && event.date() == null));
    }

    @Test
    void calculatesBmiChangesSessionTotalsAndCompletedPeriodsWithoutProjections() {
        User user = new User();
        user.setId(1L);
        user.setHeightCm(200);
        user.setLastCompletedDashboardDate(LocalDate.parse("2026-08-14"));
        Weight first = weight(1L, "2026-08-01T08:00:00+02:00", "80", "16", "20", "64", "80");
        Weight second = weight(2L, "2026-08-08T08:00:00+02:00", "76", "14", "18.42", "62", "81.58");
        Weight incompleteWeek = weight(3L, "2026-08-15T08:00:00+02:00", "72", "12", "16.67", "60", "83.33");
        Exercise squat = exercise(1L, "Squat", ExerciseTrackingMode.REPS);
        Workout workout = workout(10L, "2026-08-08", line(0, squat,
            segment(0, 10, null, "20", null, null, null, null),
            segment(1, 5, null, null, null, null, null, null)
        ));
        workout.getLines().getFirst().setCalories(100);
        workout.getLines().getFirst().setAverageHeartRate(120);

        var result = calculator.calculate(new PersonalRecordCalculator.Sources(
            user, List.of(incompleteWeek, second, first), List.of(workout), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        ));

        assertCurrent(result, PersonalRecordMetric.BODY_BMI_MINIMUM, null, null, "18");
        assertTrue(result.current().stream().anyMatch(record -> record.series().metric() == PersonalRecordMetric.CHANGE_KG_MINIMUM
            && record.series().behaviorSubject().label().equals("Weight change") && record.value().compareTo(new BigDecimal("-4")) == 0));
        assertTrue(result.current().stream().anyMatch(record -> record.series().metric() == PersonalRecordMetric.WORKOUT_SET_COUNT_MAXIMUM
            && record.series().behaviorSubject().label().equals("Workout session") && record.value().compareTo(new BigDecimal("2")) == 0));
        assertTrue(result.current().stream().anyMatch(record -> record.series().metric() == PersonalRecordMetric.WORKOUT_STRENGTH_VOLUME_MAXIMUM
            && record.value().compareTo(new BigDecimal("200")) == 0));
        assertTrue(result.history().stream().anyMatch(event -> event.series().behaviorSubject() != null
            && event.series().behaviorSubject().label().equals("Weekly average body weight")
            && event.date().equals(LocalDate.parse("2026-08-14"))));
        assertTrue(result.history().stream().noneMatch(event -> event.series().behaviorSubject() != null
            && event.series().behaviorSubject().type().equals("MONTHLY")));
    }

    private void assertCurrent(PersonalRecordCalculator.Calculation result, PersonalRecordMetric metric, Exercise exercise, String load, String value) {
        var record = result.current().stream()
            .filter(item -> item.series().metric() == metric)
            .filter(item -> exercise == null || item.series().exercise() == exercise)
            .filter(item -> load == null ? item.series().loadKg() == null : item.series().loadKg() != null && item.series().loadKg().compareTo(new BigDecimal(load)) == 0)
            .findFirst().orElseThrow();
        assertEquals(0, record.value().compareTo(new BigDecimal(value)));
    }

    private Weight weight(Long id, String date, String weight, String fat, String fatPercentage, String muscle, String musclePercentage) {
        Weight result = new Weight();
        result.setId(id);
        result.setMeasuredAt(OffsetDateTime.parse(date));
        result.setWeight(new BigDecimal(weight));
        result.setFat(new BigDecimal(fat));
        result.setFatPercentage(new BigDecimal(fatPercentage));
        result.setMuscle(new BigDecimal(muscle));
        result.setMusclePercentage(new BigDecimal(musclePercentage));
        return result;
    }

    private Exercise exercise(Long id, String name, ExerciseTrackingMode mode) {
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setName(name);
        exercise.setTrackingMode(mode);
        return exercise;
    }

    private Workout workout(Long id, String date, WorkoutLine... lines) {
        Workout workout = new Workout();
        workout.setId(id);
        workout.setWorkoutDate(LocalDate.parse(date));
        for (WorkoutLine line : lines) {
            line.setWorkout(workout);
            workout.getLines().add(line);
        }
        return workout;
    }

    private WorkoutLine line(int position, Exercise exercise, WorkoutSegment... segments) {
        WorkoutLine line = new WorkoutLine();
        line.setPosition(position);
        line.setExercise(exercise);
        for (WorkoutSegment segment : segments) {
            segment.setWorkoutLine(line);
            line.getSegments().add(segment);
        }
        return line;
    }

    private WorkoutSegment segment(int position, Integer repetitions, Integer duration, String weight, String speed, String distance, String incline, Integer resistance) {
        WorkoutSegment segment = new WorkoutSegment();
        segment.setPosition(position);
        segment.setRepetitions(repetitions);
        segment.setDurationSeconds(duration);
        segment.setWeight(decimal(weight));
        segment.setSpeedKph(decimal(speed));
        segment.setDistanceKm(decimal(distance));
        segment.setInclinePercent(decimal(incline));
        segment.setResistanceLevel(resistance);
        return segment;
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private Meal meal(Long id, String date, MealType type, int calories, String protein, String carbohydrates, String fat) {
        Meal meal = new Meal();
        meal.setId(id); meal.setMealDate(LocalDate.parse(date)); meal.setMealType(type); meal.setMealSequence(1); meal.setCalories(calories);
        meal.setProteinGrams(decimal(protein)); meal.setCarbohydrateGrams(decimal(carbohydrates)); meal.setFatGrams(decimal(fat));
        return meal;
    }

    private HabitCheckin habitCheckin(Long id, Habit habit, String date) {
        HabitCheckin checkin = new HabitCheckin();
        checkin.setId(id); checkin.setHabit(habit); checkin.setCheckinDate(LocalDate.parse(date));
        return checkin;
    }

    private RoutineCheckin routineCheckin(Long id, Routine routine, String date) {
        RoutineCheckin checkin = new RoutineCheckin();
        checkin.setId(id); checkin.setRoutine(routine); checkin.setCheckedAt(OffsetDateTime.parse(date));
        return checkin;
    }

    private DecisionOutcome decision(Long id, User user, String date, DecisionOutcomeType outcome) {
        DecisionOutcome result = new DecisionOutcome();
        result.setId(id); result.setUser(user); result.setOutcomeDate(LocalDate.parse(date)); result.setOutcome(outcome);
        return result;
    }
}
