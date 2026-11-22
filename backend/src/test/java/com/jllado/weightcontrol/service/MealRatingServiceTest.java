package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jllado.weightcontrol.api.dto.MealRatingDtos.Preferences;
import com.jllado.weightcontrol.domain.DishUnit;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealDish;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.repository.MealRepository.Timing;
import com.jllado.weightcontrol.repository.MealRatingProfileHistory.Targets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MealRatingServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);
    private static final Preferences DEFAULTS = Preferences.defaults();
    private static final Targets TARGETS = new Targets(2000, new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("5"), null);
    private static final MealBudget EMPTY = new MealBudget(BigDecimal.ZERO, BigDecimal.ZERO);
    private static final MealRatingTiming NO_TIMING = new MealRatingTiming(false, "Unavailable.", null);

    @ParameterizedTest
    @CsvSource({"1300,10", "1650,5", "2000,1", "2800,1"})
    void sizeScoresOnlyTheRecordedMainMealOnALinearScale(int kcal, int expected) {
        var meal = meal(MealType.DINNER, kcal, "20:00");
        assertEquals(expected, MealRatingService.score(meal, TARGETS, DEFAULTS, NO_TIMING, EMPTY).score());
        meal.setMealType(MealType.SNACK);
        var result = MealRatingService.score(meal, TARGETS, DEFAULTS, new MealRatingTiming(true, "Gap missed.", "Allow a longer gap."), EMPTY);
        assertNull(result.score());
        assertTrue(result.explanation().contains("no assessable food or portion"));
    }

    @Test void scalesGramsToTheCalorieShareAndSkipsIncompleteNutrientsInBothDirections() {
        var meal = meal(MealType.SNACK, 400, "22:45");
        meal.setProteinGrams(BigDecimal.TEN);
        var first = food("Bread", 1, DishUnit.UNIT);
        first.setFiberGrams(new BigDecimal("2"));
        first.setSaltGrams(new BigDecimal("1.5"));
        first.setSaturatedFatGrams(new BigDecimal("8"));
        meal.getDishes().add(first);
        var result = MealRatingService.score(meal, TARGETS, DEFAULTS, NO_TIMING, EMPTY);
        // Protein, fiber and salt each earn .5. Saturated fat earns .2 at 18% of energy.
        assertEquals(4, result.score());
        assertTrue(result.explanation().contains("1.7/4 assessable points"));
        meal.getDishes().add(food("Tomato", 1, DishUnit.UNIT));
        result = MealRatingService.score(meal, TARGETS, DEFAULTS, NO_TIMING, EMPTY);
        assertEquals(5, result.score());
        assertTrue(result.explanation().contains("0.5/1 assessable points"));
        assertTrue(result.explanation().contains("1/2 foods recorded"));
    }

    @Test void novemberDinnerIsRatedAgainstTheRemainingDailyCaloriesAndCarbohydrates() {
        var targets = new Targets(2589, BigDecimal.valueOf(115), BigDecimal.valueOf(25), BigDecimal.valueOf(5), BigDecimal.valueOf(30));
        var dinner = meal(MealType.DINNER, 927, "21:00");
        dinner.setProteinGrams(new BigDecimal("48.43"));
        dinner.setCarbohydrateGrams(new BigDecimal("155.61"));
        var portion = food("Mixed dish", 1, DishUnit.SERVING);
        portion.setFiberGrams(new BigDecimal("22.61"));
        portion.setSaltGrams(new BigDecimal("3.01"));
        portion.setSaturatedFatGrams(new BigDecimal("6.95"));
        dinner.getDishes().add(portion);
        var prior = new MealBudget(BigDecimal.valueOf(2201), new BigDecimal("272.67"));
        var result = MealRatingService.score(dinner, targets, DEFAULTS, NO_TIMING, prior);
        assertEquals(6, result.score());
        assertTrue(result.explanation().contains("388 left, meal 927, 539 kcal of this meal over budget"));
        assertTrue(result.explanation().contains("65.41 left, meal 155.61, 90.2 g of this meal over budget"));
        assertEquals(9, MealRatingService.score(dinner, targets, DEFAULTS, NO_TIMING, EMPTY).score());
    }

    @Test void earlierMissingCarbsSkipOnlyCarbsAndUnknownOrderSkipsBothBudgets() {
        var snack = meal(MealType.SNACK, 100, "15:00");
        var earlier = meal(MealType.LUNCH, 500, "12:00");
        earlier.setId(1L);
        var later = meal(MealType.DINNER, 600, "21:00");
        later.setId(3L);
        var day = List.of(later, snack, earlier);
        assertEquals(new MealBudget(BigDecimal.valueOf(500), null), MealBudget.before(snack, day));
        earlier.setCarbohydrateGrams(BigDecimal.TEN);
        assertEquals(new MealBudget(BigDecimal.valueOf(500), BigDecimal.TEN), MealBudget.before(snack, day));
        later.setMealTime(null);
        assertEquals(new MealBudget(null, null), MealBudget.before(snack, day));
    }

    @Test void eachDeductionUsesOnlyNewExcessAndEqualityMeetsTheLimit() {
        var limit = BigDecimal.TEN;
        assertEquals(0, MealBudget.impact("carbs", "g", limit, BigDecimal.ONE, BigDecimal.valueOf(9)).deduction());
        assertEquals(2, MealBudget.impact("carbs", "g", limit, BigDecimal.valueOf(20), BigDecimal.ONE).deduction());
        assertEquals(0, MealBudget.impact("carbs", "g", limit, BigDecimal.valueOf(20), BigDecimal.ZERO).deduction());
        assertEquals(1, MealBudget.impact("carbs", "g", limit, BigDecimal.valueOf(9), BigDecimal.valueOf(2)).deduction());
        assertEquals(0, MealBudget.impact("carbs", "g", null, BigDecimal.ZERO, BigDecimal.TEN).deduction());
    }

    @Test void identifiablePlateUsesAmountsAcrossUnitsAndReportsOpaqueDishesAsUnknown() {
        var meal = meal(MealType.LUNCH, 500, "14:00");
        meal.getDishes().add(food("Carrots", 130, DishUnit.GRAM)); // approximately 200 mL
        meal.getDishes().add(food("Eggs", 2, DishUnit.UNIT));
        meal.getDishes().add(food("Cooked rice", 100, DishUnit.MILLILITRE));
        var result = MealRatingService.score(meal, TARGETS, DEFAULTS, NO_TIMING, EMPTY);
        assertTrue(result.explanation().contains("Non-starchy vegetables: 50%"));
        assertTrue(result.explanation().contains("Protein foods: 25%"));
        assertTrue(result.explanation().contains("Starchy foods: 25%"));
        assertEquals(10, result.score());
        meal.getDishes().add(food("Chicken and rice casserole", 100, DishUnit.GRAM));
        result = MealRatingService.score(meal, TARGETS, DEFAULTS, NO_TIMING, EMPTY);
        assertTrue(result.explanation().contains("3/4 foods identified"));
        assertTrue(result.explanation().contains("Non-starchy vegetables: skipped"));
    }

    @Test void classifiesNamedIngredientsAndKeepsAmbiguousMixedFoodsUnknown() {
        var plate = new MealPlateEstimate(List.of(
            food("Brócoli con aceite de oliva", 150, DishUnit.MILLILITRE),
            food("Grilled squid", 50, DishUnit.MILLILITRE),
            food("Merluza", 25, DishUnit.MILLILITRE),
            food("Wholegrain spaghetti", 75, DishUnit.MILLILITRE),
            food("Apple juice", 200, DishUnit.MILLILITRE)));
        assertEquals(.5, plate.share(MealPlateEstimate.Group.VEGETABLE));
        assertEquals(.25, plate.share(MealPlateEstimate.Group.PROTEIN));
        assertEquals(.25, plate.share(MealPlateEstimate.Group.STARCH));
        assertEquals(0, new MealPlateEstimate(List.of(food("Fruit salad", 1, DishUnit.SERVING))).share(MealPlateEstimate.Group.VEGETABLE));
        for (var name : List.of("Travelc salad", "Salmon sushi", "Poke bowl", "Chicken Caesar salad")) {
            assertNull(new MealPlateEstimate(List.of(food(name, 1, DishUnit.SERVING))).share(MealPlateEstimate.Group.VEGETABLE));
        }
    }

    @Test void saturdayDinnerAndLateSnackUseTheirOwnPortionsAndPreviousSevenLocalNights() {
        var targets = new Targets(2589, BigDecimal.valueOf(115), BigDecimal.valueOf(25), null, null);
        var dinner = meal(MealType.DINNER, 1769, "21:15");
        dinner.setProteinGrams(new BigDecimal("88.2"));
        var names = List.of("Mercadona hamburger patties", "Mercadona regular hamburger buns", "Mercadona hot-dog sausages",
            "Mercadona hot-dog buns", "Fried potatoes", "Banana", "Mandarins");
        var grams = List.of(200, 120, 100, 100, 50, 136, 300);
        for (int i = 0; i < names.size(); i++) {
            var food = food(names.get(i), grams.get(i), DishUnit.GRAM);
            food.setFruit(i >= 5);
            food.setFiberGrams(BigDecimal.ZERO);
            dinner.getDishes().add(food);
        }
        dinner.getDishes().getFirst().setFiberGrams(new BigDecimal("16.74"));
        var sleeps = new ArrayList<Sleep>();
        for (String clock : List.of("22:34", "22:59", "22:30", "23:07", "22:55", "23:18", "22:40")) {
            sleeps.add(night(DAY.minusDays(7).plusDays(sleeps.size()), clock));
        }
        sleeps.add(night(DAY.plusDays(1), "00:06"));
        var dinnerTiming = MealRatingTiming.evaluate(dinner, DEFAULTS, List.of(), sleeps);
        assertFalse(dinnerTiming.missed());
        assertTrue(dinnerTiming.explanation().contains("usual bedtime 22:55, gap 70 min"));
        var dinnerScore = MealRatingService.score(dinner, targets, DEFAULTS, dinnerTiming, EMPTY);
        assertTrue(dinnerScore.explanation().contains("Non-starchy vegetables: 0%"));
        assertTrue(dinnerScore.explanation().contains("1769 / 2589 kcal (68.33%"));
        assertTrue(dinnerScore.explanation().contains("Next step: Fill about half"));
        assertTrue(dinnerScore.score() < 8);

        var snack = meal(MealType.SNACK, 473, "22:45");
        snack.setProteinGrams(new BigDecimal("29.3"));
        var snackFood = food("Yogurt", 1, DishUnit.SERVING);
        snackFood.setFiberGrams(new BigDecimal("8.74"));
        snack.getDishes().add(snackFood);
        var timing = MealRatingTiming.evaluate(snack, DEFAULTS, List.of(new Timing(1, DAY, dinner.getMealTime(), 30, MealType.DINNER)), sleeps);
        assertTrue(timing.missed());
        assertTrue(timing.explanation().contains("Previous meal end to start 60 min"));
        assertTrue(timing.explanation().contains("usual bedtime 22:55, gap -20 min"));
        assertEquals(9, MealRatingService.score(snack, targets, DEFAULTS, timing, EMPTY).score());
    }

    @Test void localMedianHandlesMidnightAndEqualityMeetsBothTimePreferences() {
        var dinner = meal(MealType.DINNER, 500, "22:30");
        var nights = List.of(night(DAY.minusDays(1), "23:50"), night(DAY.minusDays(1), "00:10"));
        var median = MealRatingTiming.medianBedtime(DAY, nights);
        assertEquals(DAY.plusDays(1).atStartOfDay(), median);
        assertFalse(MealRatingTiming.evaluate(dinner, DEFAULTS, List.of(new Timing(1, DAY, LocalTime.of(20, 30), 30, MealType.LUNCH)), nights).missed());
        dinner.setMealTime(LocalTime.of(22, 31));
        assertTrue(MealRatingTiming.evaluate(dinner, DEFAULTS, List.of(), nights).missed());
        var afterMidnight = meal(MealType.SNACK, 250, "00:01");
        afterMidnight.setMealDate(DAY.plusDays(1));
        assertTrue(MealRatingTiming.evaluate(afterMidnight, DEFAULTS, List.of(), nights).missed());
        assertFalse(MealRatingTiming.evaluate(afterMidnight, DEFAULTS, List.of(), List.of()).missed());
    }

    @Test void estimatedHistoricalMealTotalCanAssessSaturatedFatWithoutInventingFoodCoverage() {
        var snack = meal(MealType.SNACK, 900, "11:00");
        snack.setEstimatedSaturatedFatGrams(BigDecimal.TEN);
        snack.setSaturatedFatEstimateSource("Comparable meal estimate");
        var result = MealRatingService.score(snack, TARGETS, DEFAULTS, NO_TIMING, EMPTY);
        assertEquals(10, result.score());
        assertTrue(result.explanation().contains("estimated meal total: Comparable meal estimate"));
    }

    private static Meal meal(MealType type, int kcal, String time) {
        var meal = new Meal();
        meal.setId(2L);
        meal.setMealType(type);
        meal.setMealDate(DAY);
        meal.setMealTime(LocalTime.parse(time));
        meal.setDurationMinutes(30);
        meal.setCalories(kcal);
        return meal;
    }

    private static MealDish food(String name, int quantity, DishUnit unit) {
        var food = new MealDish();
        food.setName(name);
        food.setQuantity(BigDecimal.valueOf(quantity));
        food.setUnit(unit);
        return food;
    }

    private static Sleep night(LocalDate date, String time) {
        var sleep = new Sleep();
        sleep.setBedtimeStart(OffsetDateTime.of(date, LocalTime.parse(time), ZoneOffset.ofHours(-4)));
        return sleep;
    }
}
