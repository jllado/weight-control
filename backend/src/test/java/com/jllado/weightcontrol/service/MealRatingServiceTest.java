package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealDish;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository.Intake;
import com.jllado.weightcontrol.repository.MealRatingProfileHistory.Targets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MealRatingServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 8, 12);

    @ParameterizedTest
    @CsvSource({"100,8", "101,5", "110,5", "111,2"})
    void calorieCutoffsIncludeTheBoundaryForBothDatedLimits(int calories, int expected) {
        var meal = meal(calories);
        score(meal, new User(), 100, 100);
        assertEquals(expected, meal.getRating());
    }

    @ParameterizedTest
    @CsvSource({"10,2,10", "5,1,8", "4.99,0.99,6"})
    void comparesCompositionAgainstPersonalTargetsWithoutIntermediateRounding(String protein, String fiber, int expected) {
        var owner = new User();
        owner.setMacroProteinTargetGrams(BigDecimal.valueOf(100));
        owner.setNutrientFiberTargetGrams(BigDecimal.valueOf(20));
        var meal = meal(200);
        meal.setProteinGrams(new BigDecimal(protein));
        meal.getDishes().add(food(new BigDecimal(fiber)));

        score(meal, owner, 2000, 2000);
        assertEquals(expected, meal.getRating());
        assertTrue(meal.getRatingExplanation().contains("personal reference 100 g/2000 kcal"));
    }

    @Test
    void distinguishesPartialFiberEvidenceAndClampsTheLowestScore() {
        var meal = meal(100);
        var known = food(BigDecimal.ZERO);
        meal.getDishes().add(known);
        meal.getDishes().add(food(null));
        var user = new User();
        score(meal, user, 100, 100);
        assertEquals(8, meal.getRating());
        assertTrue(meal.getRatingExplanation().contains("at least 0 g"));
        known.setFiberGrams(BigDecimal.valueOf(1.4));
        score(meal, user, 100, 100);
        assertEquals(9, meal.getRating());

        meal.setProteinGrams(BigDecimal.ZERO);
        known.setFiberGrams(BigDecimal.ZERO);
        meal.getDishes().removeLast();
        score(meal, user, 0, 0);
        assertEquals(1, meal.getRating());
    }

    @Test
    void untimedMealsFollowAllTimedMealsAndUseStorageOrderAmongThemselves() {
        var meal = meal(0);
        meal.setMealTime(null);
        meal.setProteinGrams(BigDecimal.ONE);
        var history = List.of(new Intake(1, DAY, LocalTime.MAX, 80), new Intake(2, DAY, null, 0),
            new Intake(3, DAY, LocalTime.MIN, 10), new Intake(4, DAY, null, 5000));
        MealRatingService.score(meal, new Targets(100, 100, null, null), DAY, history);
        assertEquals(8, meal.getRating());
        assertTrue(meal.getRatingExplanation().contains("90 / 100 kcal"));
        assertTrue(meal.getRatingExplanation().contains("zero meal calories"));
    }

    private static void score(Meal meal, User user, int budget, int weeklyCap) {
        var intake = List.of(new Intake(meal.getId(), DAY, meal.getMealTime(), meal.getCalories()));
        MealRatingService.score(meal, new Targets(budget, weeklyCap, user.getMacroProteinTargetGrams(), user.getNutrientFiberTargetGrams()), DAY, intake);
    }

    private static Meal meal(int calories) {
        var meal = new Meal();
        meal.setId(2L);
        meal.setMealDate(DAY);
        meal.setMealTime(LocalTime.NOON);
        meal.setCalories(calories);
        return meal;
    }

    private static MealDish food(BigDecimal fiber) {
        var food = new MealDish();
        food.setFiberGrams(fiber);
        return food;
    }
}
