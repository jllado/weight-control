package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.MealRepository.Intake;
import com.jllado.weightcontrol.repository.MealRatingProfileHistory;
import com.jllado.weightcontrol.repository.MealRatingProfileHistory.Targets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MealRatingService {
    private static final Comparator<LocalTime> MEAL_TIME = Comparator.nullsLast(Comparator.naturalOrder());

    public static final String RULES = """
        Start at 5 and clamp the total to 1–10. Compare meal protein and fiber density separately with their personal daily gram goal per dated calorie target, or with 5 g protein and 1.4 g fiber per 100 kcal when that ratio is unavailable. Each earns +1 at or above its reference, −1 below half, otherwise 0. Missing amounts or zero meal calories are neutral. Partial fiber can earn +1 only from its known subtotal.
        Sum the owner's recorded intake through this meal, using known meal times first, earliest to latest, followed by untimed meals. Break ties by storage order. For the meal date, award +2 at or below the effective calorie target, 0 through 110%, otherwise −2. From the dated planning-week start, compare average intake across recorded days only with the weekly average maximum: +1 at or below, 0 through 110%, otherwise −1. The meal date counts through this meal only. Use the dated calorie plan first, then profile targets known effective by the meal date. A missing dated calorie target or maximum is neutral. Score and explanation are saved together on creation or edit.
        """.strip();

    private final MealRepository meals;
    private final WeeklyCaloriePlanService plans;

    private final MealRatingProfileHistory profiles;

    public MealRatingService(MealRepository meals, WeeklyCaloriePlanService plans, MealRatingProfileHistory profiles) {
        this.profiles = profiles;
        this.meals = meals;
        this.plans = plans;
    }

    // All meals in a copy-day batch are inserted before scoring, so time takes precedence over payload order.
    void apply(User user, List<Meal> savedOnDate) {
        LocalDate date = savedOnDate.getFirst().getMealDate();
        LocalDate start = PlanningWeekService.period(user, date).start();
        profiles.captureInitial(user);
        var profile = profiles.find(user, date).orElse(new Targets(null, null, null, null));
        var savedTarget = plans.savedCalories(user, date);
        var target = new Targets(savedTarget == null ? profile.weekdayCalories() : savedTarget, profile.weeklyMaximum(),
            profile.proteinGrams(), profile.fiberGrams());
        var intake = meals.findIntakeByUserAndMealDateBetween(user, start, date);
        for (var meal : savedOnDate) {
            score(meal, target, start, intake);
        }
    }

    static void score(Meal meal, Targets target, LocalDate start, List<Intake> intake) {
        Map<LocalDate, Long> caloriesByDate = new HashMap<>();
        for (var entry : intake) {
            if (entry.mealDate().isBefore(meal.getMealDate()) || throughMeal(entry, meal)) {
                caloriesByDate.merge(entry.mealDate(), (long) entry.calories(), Long::sum);
            }
        }
        long dayCalories = caloriesByDate.get(meal.getMealDate());
        long weekCalories = caloriesByDate.values().stream().mapToLong(Long::longValue).sum();
        int dayPoints = target.weekdayCalories() == null ? 0 : 2 * compareCalories(dayCalories, target.weekdayCalories());
        int weekPoints = target.weeklyMaximum() == null ? 0 : compareCalories(weekCalories, (long) target.weeklyMaximum() * caloriesByDate.size());

        var nutrients = NutrientSummaryService.summarize(List.of(meal));
        var protein = composition("Protein", meal.getProteinGrams(), true, meal.getCalories(), target.proteinGrams(), target.weekdayCalories(), BigDecimal.valueOf(5));
        var fiber = composition("Fiber", nutrients.fiberGrams(), nutrients.fiberCoverage().foodsWithValues() == nutrients.totalFoods(),
            meal.getCalories(), target.fiberGrams(), target.weekdayCalories(), BigDecimal.valueOf(1.4));

        meal.setRating(Math.clamp(5 + protein.points() + fiber.points() + dayPoints + weekPoints, 1, 10));
        meal.setRatingExplanation("Base 5. " + protein.explanation() + " " + fiber.explanation()
            + " Day through meal: " + dayCalories + " / " + (target.weekdayCalories() == null ? "unknown target" : target.weekdayCalories() + " kcal") + " (" + signed(dayPoints) + ")."
            + " Week from " + start + ": " + weekCalories + " kcal across " + caloriesByDate.size()
            + (caloriesByDate.size() == 1 ? " recorded day" : " recorded days") + ", cap " + (target.weeklyMaximum() == null ? "unknown" : target.weeklyMaximum() + " kcal/day") + " (" + signed(weekPoints) + ").");
    }

    private static boolean throughMeal(Intake entry, Meal meal) {
        int timeOrder = MEAL_TIME.compare(entry.mealTime(), meal.getMealTime());
        return timeOrder < 0 || (timeOrder == 0 && entry.id() <= meal.getId());
    }

    private static int compareCalories(long calories, long limit) {
        if (calories <= limit) return 1;
        return calories <= limit * 11 / 10 ? 0 : -1;
    }

    private static Component composition(String name, BigDecimal grams, boolean complete, int calories,
        BigDecimal personalGrams, Integer budget, BigDecimal standardGrams) {
        if (grams == null || calories == 0) {
            String reason = grams == null ? "amount unavailable" : "zero meal calories";
            return new Component(0, name + ": " + reason + " (0).");
        }
        boolean personal = personalGrams != null && budget != null && budget > 0;
        BigDecimal reference = personal ? personalGrams : standardGrams;
        int referenceCalories = personal ? budget : 100;
        BigDecimal amount = grams.multiply(BigDecimal.valueOf(referenceCalories));
        BigDecimal threshold = reference.multiply(BigDecimal.valueOf(calories));
        int points = 0;
        if (amount.compareTo(threshold) >= 0) points = 1;
        else if (complete && amount.multiply(BigDecimal.TWO).compareTo(threshold) < 0) points = -1;
        String explanation = name + ": " + (complete ? "" : "at least ") + number(grams) + " g in " + calories + " kcal, "
            + (personal ? "personal" : "standard") + " reference " + number(reference) + " g/" + referenceCalories + " kcal (" + signed(points) + ").";
        return new Component(points, explanation);
    }

    private static String number(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String signed(int points) {
        return points > 0 ? "+" + points : String.valueOf(points);
    }

    private record Component(int points, String explanation) { }
}
