package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.domain.Exercise;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.StretchingUnit;
import com.jllado.weightcontrol.domain.Workout;
import com.jllado.weightcontrol.domain.WorkoutLine;
import com.jllado.weightcontrol.domain.WorkoutSegment;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyMetricsCalculatorTest {

    private final WeeklyMetricsCalculator calculator = new WeeklyMetricsCalculator();

    @Test
    void completeWeekUsesOpportunityWeightedCompletionAndRecordedDayAverages() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        List<DailyStatus> statuses = statuses(end.minusDays(6), new int[][]{
            {2, 3}, {1, 3}, {3, 3}, {0, 3}, {2, 3}, {3, 3}, {3, 3}
        });
        Mood morning = mood(end.minusDays(1), MoodPeriod.MORNING, 1);
        Mood evening = mood(end.minusDays(1), MoodPeriod.EVENING, 5);
        Mood friday = mood(end, MoodPeriod.MORNING, 5);
        WeeklyMetricsCalculator.Input input = new WeeklyMetricsCalculator.Input(
            statuses,
            List.of(weight(user, end, "68.20"), weight(user, end, "68.80")),
            List.of(),
            List.of(morning, evening, friday),
            List.of(),
            List.of(
                new CalorieService.DailyCalories(end.minusDays(1), 0, new BigDecimal("30"), null, new BigDecimal("12")),
                new CalorieService.DailyCalories(end, 2000, new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("20"))
            ),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );

        WeeklyMetrics.Summary summary = calculator.progress(user, end, input).currentPeriod();

        assertEquals(14, summary.routineCompletion().completed());
        assertEquals(21, summary.routineCompletion().opportunities());
        assertEquals(0, new BigDecimal("66.67").compareTo(summary.routineCompletion().percentage()));
        assertEquals(7, summary.routineCompletion().days().size());
        assertEquals(2, summary.calories().entryCount());
        assertEquals(0, new BigDecimal("1000.00").compareTo(summary.calories().averageCalories()));
        assertEquals(0, new BigDecimal("40").compareTo(summary.calories().averageProteinGrams()));
        assertEquals(2, summary.calories().proteinDayCount());
        assertEquals(0, new BigDecimal("100").compareTo(summary.calories().averageCarbohydrateGrams()));
        assertEquals(1, summary.calories().carbohydrateDayCount());
        assertEquals(0, new BigDecimal("16").compareTo(summary.calories().averageFatGrams()));
        assertEquals(2, summary.calories().fatDayCount());
        assertEquals(2, summary.moodDayCount());
        assertEquals(0, new BigDecimal("4.00").compareTo(summary.moodAverage()));
        assertEquals(2, summary.weight().measurementCount());
        assertEquals(0, new BigDecimal("68.50").compareTo(summary.weight().weightKg()));
        assertEquals(0, new BigDecimal("13.70").compareTo(summary.weight().fatKg()));
        assertEquals(0, new BigDecimal("27.40").compareTo(summary.weight().muscleKg()));
    }

    @Test
    void comparisonsUsePreviousAndWeekdayAlignedYearAgoPeriods() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        WeeklyMetrics.Progress progress = calculator.progress(user, end, emptyInput());

        assertEquals(LocalDate.of(2026, 8, 8), progress.currentPeriod().startDate());
        assertEquals(LocalDate.of(2026, 8, 14), progress.currentPeriod().endDate());
        assertEquals(LocalDate.of(2026, 8, 1), progress.previousComparablePeriod().startDate());
        assertEquals(LocalDate.of(2026, 8, 7), progress.previousComparablePeriod().endDate());
        assertEquals(LocalDate.of(2025, 8, 9), progress.yearAgoComparablePeriod().startDate());
        assertEquals(LocalDate.of(2025, 8, 15), progress.yearAgoComparablePeriod().endDate());
        assertNull(progress.currentPeriod().routineCompletion().percentage());
    }

    @Test
    void averageStatusExcludesDaysAndCategoriesWithoutOpportunities() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        DailyStatus eligible = status(end.minusDays(6), 2, 4);
        eligible.setTotalWeightRoutines(2);
        eligible.setWeightPercentage(new BigDecimal("50.00"));
        DailyStatus noOpportunities = status(end.minusDays(5), 0, 0);
        noOpportunities.setRoutinesPercentage(BigDecimal.ZERO);
        noOpportunities.setWeightPercentage(BigDecimal.ZERO);
        WeeklyMetrics.Summary summary = calculator.progress(user, end, new WeeklyMetricsCalculator.Input(
            List.of(eligible, noOpportunities), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        )).currentPeriod();

        assertEquals(new BigDecimal("50.00"), summary.dashboard().routinesPercentage());
        assertEquals(new BigDecimal("50.00"), summary.dashboard().weightPercentage());
        assertNull(summary.dashboard().bloodPressurePercentage());
    }

    @Test
    void averageSleepPreservesRecordedStagesAndWearableMetrics() {
        User user = user();
        LocalDate end = LocalDate.of(2026, 8, 14);
        Sleep sleep = new Sleep();
        sleep.setSleepDate(end);
        sleep.setTotalSleepDuration(25200);
        sleep.setDeepSleepDuration(5400);
        sleep.setRemSleepDuration(7200);
        sleep.setLightSleepDuration(12600);
        sleep.setAwakeTime(1800);
        sleep.setAverageHeartRate(new BigDecimal("58"));
        sleep.setAverageHrv(42);

        WeeklyMetrics.AverageSleep average = calculator.progress(user, end, new WeeklyMetricsCalculator.Input(
            List.of(), List.of(), List.of(), List.of(), List.of(sleep), List.of(), List.of(), List.of(), List.of(), List.of()
        )).currentPeriod().sleep();

        assertEquals(0, new BigDecimal("5400").compareTo(average.deepSleepSeconds()));
        assertEquals(0, new BigDecimal("7200").compareTo(average.remSleepSeconds()));
        assertEquals(0, new BigDecimal("12600").compareTo(average.lightSleepSeconds()));
        assertEquals(0, new BigDecimal("1800").compareTo(average.awakeSeconds()));
        assertEquals(0, new BigDecimal("58").compareTo(average.averageHeartRate()));
        assertEquals(0, new BigDecimal("42").compareTo(average.averageHrv()));
    }

    @Test
    void countsEachPerformedTypeOncePerSessionAndSumsOnlyActualCardioSeconds() {
        Workout mixed = workout(LocalDate.of(2026, 8, 14),
            line(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, 12, false),
            line(ExerciseType.TRAINING, ExerciseTrackingMode.SECONDS, 20, false),
            line(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, 30, false),
            line(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, 60, false),
            line(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, 45, false));
        mixed.setCardioMinutes(90);
        Workout timedStrength = workout(mixed.getWorkoutDate(), line(ExerciseType.TRAINING, ExerciseTrackingMode.SECONDS, 300, false));
        Workout cardio = workout(mixed.getWorkoutDate(),
            line(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, 12, true),
            line(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, 60, false),
            line(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, null, false),
            line(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, 600, true));
        WorkoutLine breaths = line(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, null, false);
        breaths.setStretchingUnit(StretchingUnit.BREATHS);
        breaths.getSegments().getFirst().setBreaths(6);
        Workout stretching = workout(mixed.getWorkoutDate(), breaths);
        Workout warmUp = workout(mixed.getWorkoutDate(), line(ExerciseType.WARM_UP, ExerciseTrackingMode.SECONDS, 120, false));
        Workout sauna = workout(mixed.getWorkoutDate());
        sauna.setSaunaSession(true);
        sauna.setSaunaRoundsMinutes(List.of(15));
        Workout skipped = workout(mixed.getWorkoutDate(), line(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, 60, true));

        JsonNode result = new ObjectMapper().valueToTree(calculator.summarizeWorkouts(List.of(mixed, timedStrength, cardio, stretching, warmUp, sauna, skipped)));

        assertEquals(7, result.path("workoutCount").asInt(-1));
        assertEquals(2, result.path("strengthSessionCount").asInt(-1));
        assertEquals(2, result.path("cardioSessionCount").asInt(-1));
        assertEquals(2, result.path("stretchingSessionCount").asInt(-1));
        assertEquals(90, result.path("activeCardioSeconds").asInt(-1));
        assertEquals(false, result.path("cardioDurationComplete").asBoolean(true));
    }

    @Test
    void capturesEmptyWorkoutActivityAsObservedZeroAndUsesInclusiveComparisonWindows() {
        LocalDate end = LocalDate.of(2026, 8, 14);
        List<Workout> workouts = List.of(
            workout(end.minusDays(6), line(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, 12, false)),
            workout(end, line(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, 90, false)),
            workout(end.minusDays(7), line(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, 30, false)),
            workout(end.minusDays(13), line(ExerciseType.STRETCHING, ExerciseTrackingMode.SECONDS, 30, false)),
            workout(end.minusDays(364), line(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, 60, false)),
            workout(end.minusDays(370), line(ExerciseType.TRAINING, ExerciseTrackingMode.CARDIO, 30, false)),
            workout(end.plusDays(1), line(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, 12, false)),
            workout(end.minusDays(371), line(ExerciseType.TRAINING, ExerciseTrackingMode.REPS, 12, false)));
        JsonNode progress = new ObjectMapper().findAndRegisterModules().valueToTree(calculator.progress(user(), end,
            new WeeklyMetricsCalculator.Input(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), workouts, List.of(), List.of(), List.of())));

        assertEquals(1, progress.at("/currentPeriod/workouts/strengthSessionCount").asInt(-1));
        assertEquals(1, progress.at("/currentPeriod/workouts/cardioSessionCount").asInt(-1));
        assertEquals(2, progress.at("/previousComparablePeriod/workouts/stretchingSessionCount").asInt(-1));
        assertEquals(2, progress.at("/yearAgoComparablePeriod/workouts/cardioSessionCount").asInt(-1));
        assertEquals(90, progress.at("/yearAgoComparablePeriod/workouts/activeCardioSeconds").asInt(-1));
        JsonNode empty = new ObjectMapper().valueToTree(calculator.summarizeWorkouts(List.of()));
        for (String field : List.of("strengthSessionCount", "cardioSessionCount", "stretchingSessionCount", "activeCardioSeconds")) assertEquals(0, empty.path(field).asInt(-1));
        assertEquals(true, empty.path("cardioDurationComplete").asBoolean(false));
    }

    private Workout workout(LocalDate date, WorkoutLine... lines) {
        Workout workout = new Workout();
        workout.setWorkoutDate(date);
        workout.setLines(List.of(lines));
        return workout;
    }

    private WorkoutLine line(ExerciseType type, ExerciseTrackingMode mode, Integer seconds, boolean skipped) {
        Exercise exercise = new Exercise();
        exercise.setExerciseType(type);
        exercise.setTrackingMode(mode);
        WorkoutSegment segment = new WorkoutSegment();
        segment.setDurationSeconds(seconds);
        segment.setSkipped(skipped);
        WorkoutLine line = new WorkoutLine();
        line.setExercise(exercise);
        line.setSegments(List.of(segment));
        return line;
    }

    private WeeklyMetricsCalculator.Input emptyInput() {
        return new WeeklyMetricsCalculator.Input(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private List<DailyStatus> statuses(LocalDate start, int[][] values) {
        List<DailyStatus> statuses = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            DailyStatus status = new DailyStatus();
            status.setStatusDate(start.plusDays(index));
            status.setRoutinesDone(values[index][0]);
            status.setTotalRoutines(values[index][1]);
            status.setRoutinesPercentage(BigDecimal.valueOf(values[index][0] * 100L).divide(BigDecimal.valueOf(values[index][1]), 2, java.math.RoundingMode.HALF_UP));
            status.setTotalWeightRoutines(0);
            status.setTotalBloodPressureRoutines(0);
            status.setTotalFlexibilityRoutines(0);
            status.setTotalMindRoutines(0);
            status.setWeightPercentage(BigDecimal.ZERO);
            status.setBloodPressurePercentage(BigDecimal.ZERO);
            status.setFlexibilityPercentage(BigDecimal.ZERO);
            status.setMindPercentage(BigDecimal.ZERO);
            statuses.add(status);
        }
        return statuses;
    }

    private DailyStatus status(LocalDate date, int completed, int opportunities) {
        DailyStatus status = new DailyStatus();
        status.setStatusDate(date);
        status.setRoutinesDone(completed);
        status.setTotalRoutines(opportunities);
        status.setRoutinesPercentage(opportunities == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(completed * 100L).divide(BigDecimal.valueOf(opportunities), 2, java.math.RoundingMode.HALF_UP));
        status.setTotalWeightRoutines(0);
        status.setTotalBloodPressureRoutines(0);
        status.setTotalFlexibilityRoutines(0);
        status.setTotalMindRoutines(0);
        status.setWeightPercentage(BigDecimal.ZERO);
        status.setBloodPressurePercentage(BigDecimal.ZERO);
        status.setFlexibilityPercentage(BigDecimal.ZERO);
        status.setMindPercentage(BigDecimal.ZERO);
        return status;
    }

    private Mood mood(LocalDate date, MoodPeriod period, int value) {
        Mood mood = new Mood();
        mood.setMoodDate(date);
        mood.setPeriod(period);
        mood.setValue(value);
        return mood;
    }

    private Weight weight(User user, LocalDate date, String value) {
        Weight weight = new Weight();
        weight.setUser(user);
        weight.setMeasuredAt(DateTimes.startOfDay(date).plusHours(8));
        weight.setWeight(new BigDecimal(value));
        weight.setFatPercentage(new BigDecimal("20.00"));
        weight.setFat(new BigDecimal(value).multiply(new BigDecimal("0.20")));
        weight.setMusclePercentage(new BigDecimal("40.00"));
        weight.setMuscle(new BigDecimal(value).multiply(new BigDecimal("0.40")));
        return weight;
    }

    private User user() {
        User user = new User();
        user.setTypicalCaloriesSaturday(2000);
        user.setTypicalCaloriesSunday(2000);
        user.setTypicalCaloriesMonday(2000);
        user.setTypicalCaloriesTuesday(2000);
        user.setTypicalCaloriesWednesday(2000);
        user.setTypicalCaloriesThursday(2000);
        user.setTypicalCaloriesFriday(2000);
        return user;
    }
}
