package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.MacroTargetComparison;
import com.jllado.weightcontrol.domain.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

final class WeeklySummaryEmailFixtures {
    private WeeklySummaryEmailFixtures() { }
    static WeeklySummarySnapshot populated() {
        LocalDate start = LocalDate.of(2026, 10, 17);
        var current = period(start, 24, "2450", "21600", "68.2", "17.000", "4.0", 5, 3, 3, 4, 5490);
        var previous = period(start.minusWeeks(1), 21, "2600", "21000", "68.5", "17.3", "3.5", 4, 2, 2, 3, 5400);
        var year = period(start.minusWeeks(52), 28, "2400", "22000", "69.1", "18", "4.1", 6, 4, 3, 2, 7200);
        var user = new User(); user.setWeeklyAverageCalorieMaximum(2500); user.setInShapeBodyFatLimit(new BigDecimal("17.000"));
        var captured = WeeklySummaryService.captureEmailGoals(user);
        var goals = new WeeklySummarySnapshot.EmailGoals(Instant.parse("2026-10-26T07:00:00Z"), captured.weeklyAverageCalorieMaximum(), captured.inShapeBodyFatLimit(), captured.rules());
        return new WeeklySummarySnapshot(9, start, start.plusDays(6), new WeeklyMetrics.Progress(true, current, previous, year),
            new WeeklySummarySnapshot.OutcomeMeasurements(new WeeklySummarySnapshot.WeightMeasurement(start.plusDays(7), new BigDecimal("68.1"), new BigDecimal("17.001"), new BigDecimal("11.58"), new BigDecimal("75"), new BigDecimal("51.08")),
                new WeeklySummarySnapshot.BloodPressureMeasurement(start.plusDays(8), 120, 80)),
            null, List.of(routine("Meditation", 1), routine("Walking", 2), routine("Reading", 3), routine("Stretch", 4), routine("Water", 7), routine("Bedtime", 7)),
            new WeeklySummarySnapshot.GoalEvidence(false, null, null, null, "No saved plan"), List.of(), List.of(),
            new WeeklyNutritionSnapshot(Instant.parse("2026-10-26T07:00:00Z"), "Goals saved with report", null,
                nutrients(start, false, false), nutrients(start.minusWeeks(1), true, false), nutrients(start.minusWeeks(52), true, true)),
            List.of(new WeeklySummarySnapshot.WeeklyRoutineProgress("Check weight", 1, 1, BigDecimal.valueOf(100), List.of(start.plusDays(2), start.plusDays(5))),
                new WeeklySummarySnapshot.WeeklyRoutineProgress("Check blood pressure", 0, 1, BigDecimal.ZERO, List.of())), goals);
    }

    private static WeeklySummarySnapshot.RoutineProgress routine(String name, int completed) {
        return new WeeklySummarySnapshot.RoutineProgress(name, completed, 7, BigDecimal.valueOf(completed * 100L).divide(BigDecimal.valueOf(7), 2, java.math.RoundingMode.HALF_UP));
    }
    private static WeeklyMetrics.Summary period(LocalDate start, int done, String calories, String sleep, String weight, String fat, String mood,
        int total, int strength, int cardio, int stretch, int seconds) {
        BigDecimal percentage = BigDecimal.valueOf(done * 100L).divide(BigDecimal.valueOf(42), 2, java.math.RoundingMode.HALF_UP);
        int[] counts = java.util.stream.IntStream.range(0,7).map(index -> done / 7 + (index < done % 7 ? 1 : 0)).toArray();
        var days = java.util.stream.IntStream.range(0,7).mapToObj(index -> new WeeklyMetrics.DailyRoutineCompletion(start.plusDays(index), counts[index],6,
            BigDecimal.valueOf(counts[index] * 100L).divide(BigDecimal.valueOf(6),2,java.math.RoundingMode.HALF_UP))).toList();
        return new WeeklyMetrics.Summary(start,start.plusDays(6),null,done,new WeeklyMetrics.RoutineCompletion(done,42,percentage,days),
            new WeeklyMetrics.AverageWeight(new BigDecimal(weight),new BigDecimal(fat),null,null,null,3),null,new BigDecimal(mood),7,
            new WeeklyMetrics.AverageSleep(new BigDecimal(sleep),null,null,null,null,null,7),
            new WeeklyMetrics.CalorieSummary(7,new BigDecimal(calories).multiply(BigDecimal.valueOf(7)).intValue(),new BigDecimal(calories),BigDecimal.valueOf(2400),null),
            new WeeklyMetrics.WorkoutSummary(total,18000,BigDecimal.TEN,800,BigDecimal.valueOf(1000),total,total,total,12,strength,cardio,stretch,seconds,true),
            Map.of(),Map.of(),new WeeklyMetrics.DecisionMetrics(3,1,BigDecimal.valueOf(75)));
    }
    private static WeeklyNutritionSnapshot.Period nutrients(LocalDate start, boolean previous, boolean year) {
        return new WeeklyNutritionSnapshot.Period(start,start.plusDays(6),7,7,List.of(),List.of(),List.of(
            measure("protein","Protein","g",previous ? "120" : "125",previous ? "120" : "125",7,0,MacroTargetComparison.MINIMUM),
            measure("carbohydrate","Carbohydrates","g",previous ? "250" : "260",previous ? "260" : "250",7,0,MacroTargetComparison.MAXIMUM),
            measure("fat","Fat","g","60","70",7,0,MacroTargetComparison.MAXIMUM),
            measure("vitaminD","Vitamin D","µg","0","15",7,0,MacroTargetComparison.MINIMUM),
            measure("omega3","Total omega-3","mg","1100",null,7,1,MacroTargetComparison.MINIMUM),
            measure("fiber","Fiber","g",previous ? "25" : null,"25",previous ? 7 : 0,1,MacroTargetComparison.MINIMUM),
            measure("magnesium","Magnesium","mg",year ? null : "350","350",year ? 0 : 7,0,MacroTargetComparison.MINIMUM)));
    }
    private static WeeklyNutritionSnapshot.Measure measure(String key,String label,String unit,String value,String target,int days,int estimates,MacroTargetComparison comparison) {
        return new WeeklyNutritionSnapshot.Measure(key,label,unit,value == null ? null : new BigDecimal(value),days,7,value == null ? 3 : 7,7,estimates,
            target == null ? null : new BigDecimal(target),comparison,null);
    }
}
