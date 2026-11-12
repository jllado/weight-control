package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jllado.weightcontrol.domain.User;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklySummaryVisualSummaryTest {
    private final WeeklySummaryEmailViewFactory factory = new WeeklySummaryEmailViewFactory();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private WeeklySummaryEmailView.VisualSummary visual(WeeklySummarySnapshot snapshot) {
        return factory.create(new User(),snapshot,null,"https://weight.example").visualSummary();
    }
    @Test void nutrientStatusesUseCapturedSourcesAndIndependentSubtypeEvidence() throws Exception {
        ObjectNode json = mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        ObjectNode targets = ((ObjectNode) json.get("nutrition")).putObject("targets");
        targets.putObject("vitaminD").put("value", 15).put("source", "EFSA_AI").put("referenceValue", 15);
        var measures = (com.fasterxml.jackson.databind.node.ArrayNode) json.at("/nutrition/current/measures");
        measures.addObject().put("key", "epaDha").put("label", "EPA+DHA").put("unit", "mg").put("average", 249).put("target", 250).put("eligibleDays", 7).put("comparison", "MINIMUM");
        measures.addObject().put("key", "ala").put("label", "ALA").put("unit", "mg").put("average", 1000).put("target", 1000).put("eligibleDays", 7).put("comparison", "MINIMUM");
        var snapshot = mapper.treeToValue(json, WeeklySummarySnapshot.class);
        var table = visual(snapshot).nutrition();
        assertEquals("Reference only", table.rows().get(3).status().label());
        assertEquals("Below goal", table.rows().get(4).status().label());
        assertEquals(2, table.references().size());
        ((ObjectNode) targets.get("vitaminD")).put("source", "PERSONAL");
        assertEquals("Below goal", visual(mapper.treeToValue(json, WeeklySummarySnapshot.class)).nutrition().rows().get(3).status().label());
        assertEquals(table, WeeklySummaryPresentationMapper.create(snapshot, null, null).visualSummary().nutrition());
    }
    @Test void newClassificationRulesRespectStrictBodyFatBoundariesAndLegacyRulesStayFrozen() throws Exception {
        ObjectNode json = mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        ((ObjectNode) json.at("/progress/currentPeriod/weight")).put("fatPercentage", 13);
        assertEquals("COMPETITION", visual(mapper.treeToValue(json, WeeklySummarySnapshot.class)).overview().get(2).status().label());
        var rules = (ObjectNode) json.at("/emailGoals/rules");
        rules.remove("bodyFatStages"); rules.remove("sleepStages"); rules.put("version", 1);
        assertEquals("Body-fat goal met", visual(mapper.treeToValue(json, WeeklySummarySnapshot.class)).overview().get(2).status().label());
        assertEquals("Sleep goal met", visual(mapper.treeToValue(json, WeeklySummarySnapshot.class)).overview().getFirst().status().label());
        assertNull(mapper.treeToValue(json, WeeklySummarySnapshot.class).workoutTargets());
    }

    @Test void sleepClassificationRequiresCapturedFourCriterionScoreRatherThanDurationBands() throws Exception {
        ObjectNode json = mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        var sleep = (ObjectNode) json.at("/progress/currentPeriod/sleep");
        sleep.put("totalSleepSeconds", 21600).put("statusScore", 3);
        assertEquals("GOOD", visual(mapper.treeToValue(json, WeeklySummarySnapshot.class)).overview().getFirst().status().label());
        sleep.put("statusScore", 4);
        assertEquals("EXCELLENT", visual(mapper.treeToValue(json, WeeklySummarySnapshot.class)).overview().getFirst().status().label());
        sleep.remove("statusScore");
        assertEquals("Sleep goal met", visual(mapper.treeToValue(json, WeeklySummarySnapshot.class)).overview().getFirst().status().label());
    }

    @Test void sixCardsUseCapturedInclusiveGoalsAndKeepKilogramComparisonsNeutral() {
        var snapshot = WeeklySummaryEmailFixtures.populated(); var view = visual(snapshot);
        assertEquals(List.of("sleep","calories","weight","mood","weekendWeight","bloodPressure"),view.overview().stream().map(WeeklySummaryEmailView.OverviewCard::key).toList());
        assertEquals("Sleep goal met",view.overview().get(0).status().label());
        assertTrue(view.overview().get(0).detail().contains("recorded nights"));
        assertEquals("Within calorie ceiling",view.overview().get(1).status().label());
        assertEquals("EXCELLENT",view.overview().get(2).status().label());
        assertEquals("Good",view.overview().get(3).status().label());
        assertEquals("GOOD",view.overview().get(4).status().label());
        assertEquals("NORMAL",view.overview().get(5).status().label());
        assertTrue(view.overview().get(4).detail().contains("2026"));
        assertTrue(view.overview().get(4).detail().contains("17.001%"));
        assertTrue(view.overview().get(4).detail().contains("Goal ≤ 17%"));
        assertEquals("#486277",view.overview().get(2).previous().color());
        assertEquals("-0.3 kg",view.overview().get(2).previous().text());
        assertEquals("Not available",view.overview().get(4).previous().text());
        assertTrue(view.goalsLabel().contains("Goals saved with report"));
        assertNull(view.reflection());
    }
    @Test void rawValuesDetermineStatusBeforeDisplayRounding() throws Exception {
        ObjectNode json = mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        ((ObjectNode) json.at("/progress/currentPeriod/sleep")).put("totalSleepSeconds",new BigDecimal("21599.99"));
        ((ObjectNode) json.at("/progress/currentPeriod/calories")).put("averageCalories",new BigDecimal("2500.01"));
        var view = visual(mapper.treeToValue(json,WeeklySummarySnapshot.class));
        assertEquals("6 h 0 min",view.overview().get(0).value());
        assertEquals("Below sleep goal",view.overview().get(0).status().label());
        assertEquals("2500 kcal",view.overview().get(1).value());
        assertEquals("Above calorie ceiling",view.overview().get(1).status().label());
        assertEquals("#c05621",view.overview().get(1).status().color());
    }
    @Test void legacyGoalsStayUnavailableAndLiveProfileEditsCannotChangeSavedStatuses() throws Exception {
        var snapshot = WeeklySummaryEmailFixtures.populated();
        User changed = new User(); changed.setWeeklyAverageCalorieMaximum(500);changed.setInShapeBodyFatLimit(BigDecimal.ONE);
        var view = factory.create(changed,snapshot,null,"https://weight.example").visualSummary();
        assertEquals("Within calorie ceiling",view.overview().get(1).status().label());
        assertEquals("EXCELLENT",view.overview().get(2).status().label());
        ObjectNode json = mapper.valueToTree(snapshot);json.remove("emailGoals");json.put("schemaVersion",8);
        var legacy = mapper.treeToValue(json,WeeklySummarySnapshot.class);
        assertNull(legacy.emailGoals());assertEquals(8,legacy.schemaVersion());
        var oldView = factory.create(changed,legacy,null,"https://weight.example").visualSummary();
        assertEquals("Target unavailable",oldView.overview().get(0).status().label());
        assertEquals("Target unavailable",oldView.overview().get(1).status().label());
        assertEquals("Target unavailable",oldView.overview().get(2).status().label());
        assertEquals("Rule unavailable",oldView.overview().get(5).status().label());
        assertEquals(2,oldView.weeklyRoutines().size());
        assertEquals(snapshot.emailGoals(),mapper.readValue(mapper.writeValueAsString(snapshot),WeeklySummarySnapshot.class).emailGoals());
    }
    @Test void routineAttentionUsesLowestThreeAndTotalCountWithNumericWidthsAndSeparateCadence() {
        var snapshot = WeeklySummaryEmailFixtures.populated();var view=visual(snapshot);
        assertEquals(24,view.routines().completed());assertEquals(42,view.routines().opportunities());
        assertEquals(4,view.routines().watchoutCount());
        assertEquals(List.of("Meditation","Walking","Reading"),view.routines().watchouts().stream().map(WeeklySummaryEmailView.RoutineAttention::name).toList());
        assertEquals(67,view.routines().days().getFirst().width());
        assertEquals("+7.1 pp",view.routines().previous().text());
        assertEquals(2,view.weeklyRoutines().size());
        assertTrue(view.weeklyRoutines().getFirst().dates().contains("19 Oct"));
    }
    @Test void mixedWorkoutRowsRetainIndependentCountsAndExactMinuteDeltas() {
        var rows=visual(WeeklySummaryEmailFixtures.populated()).workouts();
        assertEquals(List.of("Total sessions","Strength sessions","Cardio sessions","Stretching sessions","Active cardio minutes"),rows.stream().map(WeeklySummaryEmailView.WorkoutRow::label).toList());
        assertEquals("5",rows.get(0).current());
        assertEquals("4",rows.get(0).previous());assertEquals("+1",rows.get(0).previousDelta().text());
        assertEquals("6",rows.get(0).yearAgo());assertEquals("-1",rows.get(0).yearAgoDelta().text());
        assertEquals("0",rows.get(2).yearAgoDelta().text());
        assertEquals(List.of("3","3","4"),rows.subList(1,4).stream().map(WeeklySummaryEmailView.WorkoutRow::current).toList());
        assertEquals("91.5 min",rows.get(4).current());assertEquals("90 min",rows.get(4).previous());
        assertEquals("+1.5 min",rows.get(4).previousDelta().text());assertEquals("-28.5 min",rows.get(4).yearAgoDelta().text());
    }
    @Test void workoutPeriodsUseTheirOwnCapturedTargetsAndActualValues() throws Exception {
        ObjectNode json = mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        json.set("workoutTargets", mapper.valueToTree(new WeeklyWorkoutTargets(
            new WeeklyWorkoutTargets.Period(List.of(), 5, 3, 3, 4, 0, 5490),
            new WeeklyWorkoutTargets.Period(List.of(), 6, 3, 3, 4, 0, 6000),
            new WeeklyWorkoutTargets.Period(List.of(), 4, 2, 2, 1, 0, 3600))));
        var rows = visual(mapper.treeToValue(json, WeeklySummarySnapshot.class)).workouts();
        var strength = rows.get(1);
        assertEquals("≥ 3", strength.target()); assertEquals("Goal met", strength.status().label());
        assertEquals("≥ 3", strength.previousTarget()); assertEquals("Below goal", strength.previousStatus().label());
        assertEquals("≥ 2", strength.yearAgoTarget()); assertEquals("Goal met", strength.yearAgoStatus().label());
        var cardio = rows.get(4);
        assertEquals("≥ 91.50 min", cardio.target()); assertEquals("Goal met", cardio.status().label());
        assertEquals("≥ 100.00 min", cardio.previousTarget()); assertEquals("Below goal", cardio.previousStatus().label());
        assertEquals("≥ 60.00 min", cardio.yearAgoTarget()); assertEquals("Goal met", cardio.yearAgoStatus().label());
    }
    @Test void workoutBaselineUnknownPlansAndPartialDurationsStayNeutral() throws Exception {
        ObjectNode json = mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        json.set("workoutTargets", mapper.valueToTree(new WeeklyWorkoutTargets(
            new WeeklyWorkoutTargets.Period(List.of(), 5, 3, 3, 4, 0, 5490),
            new WeeklyWorkoutTargets.Period(List.of(), null, null, null, null, null, null),
            new WeeklyWorkoutTargets.Period(List.of(), 6, 4, 3, 2, 0, 9000))));
        ((ObjectNode) json.at("/progress/yearAgoComparablePeriod/workouts")).put("cardioDurationComplete", false);
        var rows = visual(mapper.treeToValue(json, WeeklySummarySnapshot.class)).workouts();
        assertEquals("Plan unavailable", rows.get(1).previousTarget());
        assertEquals("#486277", rows.get(1).previousStatus().color());
        assertEquals("Goal met", rows.get(1).yearAgoStatus().label());
        assertEquals("Not available", rows.get(4).yearAgoStatus().label());
        assertEquals("#486277", rows.get(4).yearAgoStatus().color());
        assertEquals("Not available", rows.get(4).yearAgoDelta().text());
    }

    @Test void partialCardioPreventsUnsupportedDeltaAndPreservesMeasuredMinutes() throws Exception {
        ObjectNode json=mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        ((ObjectNode)json.at("/progress/currentPeriod/workouts")).put("activeCardioSeconds",90).put("cardioDurationComplete",false);
        ((ObjectNode)json.at("/progress/previousComparablePeriod/workouts")).put("activeCardioSeconds",30);
        ((ObjectNode)json.at("/progress/yearAgoComparablePeriod/workouts")).putNull("activeCardioSeconds");
        var row=visual(mapper.treeToValue(json,WeeklySummarySnapshot.class)).workouts().get(4);
        assertEquals("≥ 1.5 min · Partial coverage",row.current());assertEquals("0.5 min",row.previous());
        assertEquals("Not available",row.previousDelta().text());assertEquals("Not available",row.yearAgo());
    }
    @Test void nutrientsPreserveZeroPartialEstimatesIndependentTotalAndEachPeriodsTarget() {
        var table=visual(WeeklySummaryEmailFixtures.populated()).nutrition();var rows=table.rows();
        assertEquals(List.of("protein","carbohydrate","fat","vitaminD","omega3","fiber","salt","magnesium"),rows.stream().map(WeeklySummaryEmailView.NutrientRow::key).toList());
        assertEquals("Carbs",rows.get(1).label());
        assertEquals("Goal met",rows.get(0).status().label());assertEquals("Above goal",rows.get(1).status().label());
        assertEquals("≥ 125.00 g",rows.get(0).target());assertEquals("≥ 120.00 g",rows.get(0).previousTarget());
        assertTrue(rows.get(0).previous().contains("7/7 days"));
        assertEquals("0.00 µg",rows.get(3).current());assertEquals("Not available",rows.get(3).status().label());
        assertTrue(rows.get(4).current().startsWith("≈ "));assertEquals("See report references",rows.get(4).target());
        assertEquals("#486277",rows.get(4).status().color());
        assertEquals("Not available",rows.get(5).current());assertTrue(rows.get(5).coverage().contains("Partial coverage"));
        assertEquals(1,rows.get(5).coverage().split("Partial coverage",-1).length - 1);
        assertTrue(table.hasYearAgo());
    }
    @Test void legacyMissingNutrientEvidenceNeverClaimsCapturedTargets() throws Exception {
        ObjectNode json=mapper.valueToTree(WeeklySummaryEmailFixtures.populated());json.remove("nutrition");json.remove("emailGoals");json.put("schemaVersion",5);
        var table=visual(mapper.treeToValue(json,WeeklySummarySnapshot.class)).nutrition();
        assertEquals("Nutrient evidence and targets were not captured in this report.",table.note());
        assertFalse(table.hasYearAgo());assertEquals(8,table.rows().size());
        assertTrue(table.rows().stream().allMatch(row -> row.current().equals("Not available") && row.target().equals("Target unavailable") && row.status().color().equals("#486277")));
        assertTrue(visual(WeeklySummaryEmailFixtures.populated()).nutrition().note().contains("Targets captured with report"));
    }

    @Test void displayedZeroChangesHaveNeutralSignsAndColorsWithoutChangingRawGoalStatus() throws Exception {
        ObjectNode json=mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        ((ObjectNode)json.at("/progress/currentPeriod/routineCompletion")).put("percentage",new BigDecimal("57.14"));
        ((ObjectNode)json.at("/progress/previousComparablePeriod/routineCompletion")).put("percentage",new BigDecimal("57.13"));
        ((ObjectNode)json.at("/progress/previousComparablePeriod/sleep")).put("totalSleepSeconds",21599);
        var view=visual(mapper.treeToValue(json,WeeklySummarySnapshot.class));
        assertEquals("0.0 pp",view.routines().previous().text());assertEquals("#486277",view.routines().previous().color());
        assertEquals("0 min",view.overview().getFirst().previous().text());
        assertEquals("Sleep goal met",view.overview().getFirst().status().label());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"181,120,CRISIS","180,121,CRISIS","180,120,HYPER","141,90,HYPER","140,91,HYPER","140,90,HIGH","131,80,HIGH","130,81,HIGH","130,80,ELEVATED","121,80,ELEVATED","120,80,NORMAL"})
    void pressureStagesKeepTheAppsStrictBoundaryOrdering(int systolic,int diastolic,String expected) throws Exception {
        ObjectNode json=mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        ((ObjectNode)json.at("/outcomes/bloodPressure")).put("systolic",systolic).put("diastolic",diastolic);
        assertEquals(expected,visual(mapper.treeToValue(json,WeeklySummarySnapshot.class)).overview().get(5).status().label());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"4,Good","3,Neutral","2.999,Low","3.999,Neutral"})
    void moodClassificationUsesRawSavedAverages(String average,String expected) throws Exception {
        ObjectNode json=mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        ((ObjectNode)json.at("/progress/currentPeriod")).put("moodAverage",new BigDecimal(average));
        assertEquals(expected,visual(mapper.treeToValue(json,WeeklySummarySnapshot.class)).overview().get(3).status().label());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2499.99,Within calorie ceiling","2500,Within calorie ceiling","2500.01,Above calorie ceiling"})
    void calorieCeilingUsesInclusiveRawEquality(String average,String expected) throws Exception {
        ObjectNode json=mapper.valueToTree(WeeklySummaryEmailFixtures.populated());
        ((ObjectNode)json.at("/progress/currentPeriod/calories")).put("averageCalories",new BigDecimal(average));
        assertEquals(expected,visual(mapper.treeToValue(json,WeeklySummarySnapshot.class)).overview().get(1).status().label());
    }

    @Test void newCaptureFreezesPersonalGoalsAndVersionedAppRules() {
        User user=new User();user.setWeeklyAverageCalorieMaximum(2500);user.setInShapeBodyFatLimit(new BigDecimal("17.123"));
        var goals=WeeklySummaryService.captureEmailGoals(user);
        user.setWeeklyAverageCalorieMaximum(1000);user.setInShapeBodyFatLimit(BigDecimal.ONE);
        assertEquals(new BigDecimal("2500"),goals.weeklyAverageCalorieMaximum());assertEquals(new BigDecimal("17.123"),goals.inShapeBodyFatLimit());
        assertEquals(2,goals.rules().version());assertEquals(21600,goals.rules().sleepMinimumSeconds());
        assertEquals(BigDecimal.valueOf(4),goals.rules().moodGoodMinimum());
        assertEquals(List.of("CRISIS","HYPER","HIGH","ELEVATED","NORMAL"),goals.rules().bloodPressureStages().stream().map(WeeklySummarySnapshot.BloodPressureRule::label).toList());
        assertNotNull(goals.capturedAt());
    }
}
