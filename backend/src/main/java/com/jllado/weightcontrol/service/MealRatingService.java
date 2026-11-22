package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealRatingDtos.Preferences;
import com.jllado.weightcontrol.api.dto.MealRatingDtos.RerateResult;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientCoverage;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MealRatingProfileHistory;
import com.jllado.weightcontrol.repository.MealRatingProfileHistory.Targets;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealRatingService {
    private static final int VERSION = 2;

    public static final String RULES = """
        Score the saved meal using its foods, portions and targets effective on the meal date. For main meals (default lunch/dinner), estimate plate volume from identifiable foods and consumed quantities: half non-starchy vegetables (2 points), one quarter protein and one quarter starch (0.5 each). Fruit does not count as vegetables. Known absence earns zero. Skip uncertain proportions. Main meal size earns 1 point through the configured share of the dated calorie target (default 65%), decreasing linearly to zero at 100%. Other meals skip plate and size.
        Protein and fiber each earn up to 1 point in proportion to the meal's calorie share of the dated daily gram goal. Salt uses the same calorie share of the daily limit (personal or 5 g); saturated fat uses the configured share of meal energy (default 10%, 9 kcal/g). Each limit earns 1 through the limit, decreasing linearly to zero at twice the limit. Skip missing targets, amounts and partial nutrient coverage. Normalize assessable criteria to 10.
        Deduct at most 1 point if the interval from previous meal end to this meal start is below the preference (default 90 minutes), or dinner/evening snack ends too close to usual bedtime (default 60 minutes). Use the median local bedtime of available preceding seven nights, treating times across midnight as adjacent. An evening snack follows dinner in the same evening or starts within four hours before the median bedtime or later that evening. Missing timing evidence is neutral. Round once to an integer from 1 to 10. Without assessable food or portion evidence, leave the score empty. Save the explanation and one next step with the result; later meals and sleep entries do not revise it.
        """.strip();

    private final MealRepository meals;
    private final WeeklyCaloriePlanService plans;
    private final MealRatingProfileHistory profiles;
    private final MealRatingPreferenceService preferences;
    private final SleepRepository sleeps;
    private final UserRepository users;

    public MealRatingService(MealRepository meals, WeeklyCaloriePlanService plans, MealRatingProfileHistory profiles,
        MealRatingPreferenceService preferences, SleepRepository sleeps, UserRepository users) {
        this.meals = meals;
        this.plans = plans;
        this.profiles = profiles;
        this.preferences = preferences;
        this.sleeps = sleeps;
        this.users = users;
    }

    void apply(User user, List<Meal> savedOnDate) {
        profiles.captureInitial(user);
        assessDate(user, savedOnDate, (meal, result) -> {
            meal.setRating(result.score());
            meal.setRatingExplanation(result.explanation());
            meal.setRatingVersion(VERSION);
        });
    }

    @Transactional
    public RerateResult rerate(User authenticated, boolean apply) {
        var user = users.findByIdForUpdate(authenticated.getId()).orElseThrow();
        var eligible = meals.findLegacyAutomaticRatings(user);
        var byDate = eligible.stream().collect(Collectors.groupingBy(Meal::getMealDate, LinkedHashMap::new, Collectors.toList()));
        for (var batch : byDate.values()) {
            assessDate(user, batch, (meal, result) -> {
                if (apply) meals.migrateRating(user.getId(), meal.getId(), result.score(), result.explanation(), VERSION);
            });
        }
        // Every eligible score changes from V129's Base 5 explanation to the new version.
        return new RerateResult(eligible.size(), eligible.size(), apply);
    }

    private void assessDate(User user, List<Meal> batch, java.util.function.BiConsumer<Meal, Result> save) {
        LocalDate date = batch.getFirst().getMealDate();
        var profile = profiles.find(user, date).orElse(new Targets(null, null, null, null));
        Integer saved = plans.savedCalories(user, date);
        var targets = new Targets(saved == null ? profile.weekdayCalories() : saved,
            profile.proteinGrams(), profile.fiberGrams(), profile.saltGrams());
        var prefs = preferences.forDate(user, date);
        var history = new ArrayList<MealRepository.Timing>();
        var yesterday = date.minusDays(1);
        meals.findFirstTimingByUserAndMealDateLessThanAndMealTimeIsNotNullOrderByMealDateDescMealTimeDescIdDesc(user, yesterday).ifPresent(history::add);
        history.addAll(meals.findTimingByUserAndMealDateBetweenAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user, yesterday, date));
        // Sleep dates normally refer to waking. Filter again by the original local bedtime's evening.
        var nights = sleeps.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, date.minusDays(8), date);
        for (var meal : batch) {
            var timing = MealRatingTiming.evaluate(meal, prefs, history, nights);
            save.accept(meal, score(meal, targets, prefs, timing));
        }
    }

    record Result(Integer score, String explanation) { }

    static Result score(Meal meal, Targets targets, Preferences prefs, MealRatingTiming timing) {
        var quality = new Assessment();
        var nutrients = NutrientSummaryService.summarize(List.of(meal));
        double kcal = meal.getCalories();
        Double share = targets.weekdayCalories() != null && targets.weekdayCalories() > 0 && kcal > 0
            ? kcal / targets.weekdayCalories() : null;
        if (prefs.mainMeals().contains(meal.getMealType())) {
            var plate = new MealPlateEstimate(meal.getDishes());
            quality.lines.add("Plate: " + plate.coverage() + ".");
            quality.plate("Non-starchy vegetables", plate.share(MealPlateEstimate.Group.VEGETABLE), .5, 2, "Fill about half the plate with non-starchy vegetables.");
            quality.plate("Protein foods", plate.share(MealPlateEstimate.Group.PROTEIN), .25, .5, "Include about a quarter plate of protein foods.");
            quality.plate("Starchy foods", plate.share(MealPlateEstimate.Group.STARCH), .25, .5, "Include about a quarter plate of starchy foods.");
            double limit = prefs.mainMealLimitPercent() / 100.0;
            quality.add("Main meal size", share == null ? null : (1 - share) / (1 - limit), 1,
                share == null ? "positive meal calories and dated calorie target required" : meal.getCalories() + " / " + targets.weekdayCalories() + " kcal ("
                    + number(100 * share) + "%, goal up to " + prefs.mainMealLimitPercent() + "%)",
                "Serve a smaller main meal within " + prefs.mainMealLimitPercent() + "% of the daily calorie target.");
        }
        int total = nutrients.totalFoods();
        var proteinCoverage = new NutrientCoverage((int) meal.getDishes().stream().filter(f -> f.getProteinGrams() != null).count(),
            (int) meal.getDishes().stream().filter(f -> f.getProteinGrams() != null && Boolean.TRUE.equals(f.getNutrientsEstimated())).count());
        quality.grams("Protein", meal.getProteinGrams(), scaled(targets.proteinGrams(), share), true,
            total == 0 ? "meal total" : coverage(proteinCoverage, total), "Add a portion of protein-rich food.");
        quality.grams("Fiber", nutrients.fiberGrams(), scaled(targets.fiberGrams(), share), true,
            coverage(nutrients.fiberCoverage(), total), "Add beans, vegetables or a whole grain for more fiber.",
            total != 0 && nutrients.fiberCoverage().foodsWithValues() == total);
        var dailySalt = targets.saltGrams() == null ? BigDecimal.valueOf(5) : targets.saltGrams();
        quality.grams("Salt", nutrients.saltGrams(), scaled(dailySalt, share), false,
            coverage(nutrients.saltCoverage(), total), "Choose a lower-salt version of one ingredient.",
            total != 0 && nutrients.saltCoverage().foodsWithValues() == total);
        boolean estimatedTotal = total == 0 && meal.getEstimatedSaturatedFatGrams() != null;
        quality.grams("Saturated fat", nutrients.saturatedFatGrams(), kcal > 0 ? kcal * prefs.saturatedFatEnergyPercent() / 900 : null,
            false, estimatedTotal ? "estimated meal total: " + meal.getSaturatedFatEstimateSource() : coverage(nutrients.saturatedFatCoverage(), total),
            "Replace one high-saturated-fat ingredient with an unsaturated alternative.",
            estimatedTotal || (total != 0 && nutrients.saturatedFatCoverage().foodsWithValues() == total));
        return quality.finish(timing);
    }

    private static String coverage(NutrientCoverage coverage, int total) {
        return coverage.foodsWithValues() + "/" + total + " foods recorded, " + coverage.estimatedFoods() + " estimated";
    }

    private static Double scaled(BigDecimal daily, Double share) {
        return daily == null || share == null ? null : daily.doubleValue() * share;
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static final class Assessment {
        private final List<String> lines = new ArrayList<>();
        private double awarded;
        private double possible;
        private double biggestLoss;
        private String nextStep = "Repeat these portions when planning a similar meal.";
        private String missingEvidence;

        void add(String name, Double fraction, double weight, String evidence, String action) {
            if (fraction == null) {
                lines.add(name + ": skipped (" + evidence + ").");
                if (missingEvidence == null) {
                    missingEvidence = switch (name) {
                        case "Non-starchy vegetables", "Protein foods", "Starchy foods" -> "Record the ingredients and portions of the mixed dish.";
                        case "Main meal size" -> "Record the meal calories and a daily calorie target.";
                        default -> "Complete the " + name.toLowerCase(java.util.Locale.ROOT) + " amounts and target for this meal.";
                    };
                }
                return;
            }
            double earned = weight * Math.clamp(fraction, 0, 1);
            awarded += earned;
            possible += weight;
            double lost = weight - earned;
            if (lost > biggestLoss) {
                biggestLoss = lost;
                nextStep = action;
            }
            lines.add(name + ": " + evidence + " (" + number(earned) + "/" + number(weight) + " points).");
        }

        void plate(String name, Double share, double target, double weight, String action) {
            add(name, share == null ? null : share / target, weight, share == null ? "uncertain plate proportions"
                : number(100 * share) + "% of estimated volume, goal " + number(100 * target) + "%", action);
        }

        void grams(String name, BigDecimal grams, Double goal, boolean minimum, String evidence, String action) {
            grams(name, grams, goal, minimum, evidence, action, true);
        }

        void grams(String name, BigDecimal grams, Double goal, boolean minimum, String evidence, String action, boolean complete) {
            Double fraction = null;
            if (complete && grams != null && goal != null && goal > 0) {
                double ratio = grams.doubleValue() / goal;
                fraction = minimum ? ratio : 2 - ratio;
            }
            String amount = grams == null ? "amount unknown" : number(grams.doubleValue()) + " g";
            String reference = goal == null || goal <= 0 ? "goal unavailable" : (minimum ? "target " : "limit ") + number(goal) + " g";
            add(name, fraction, 1, amount + ", " + reference + ", " + evidence, action);
        }

        Result finish(MealRatingTiming timing) {
            if (possible == 0) {
                lines.add("Timing: " + timing.explanation() + " No deduction applied.");
                return new Result(null, "Unrated: no assessable food or portion evidence.\n" + String.join("\n", lines)
                    + "\nNext step: Record identifiable foods and their portions.");
            }
            int penalty = timing.missed() ? 1 : 0;
            double normalized = 10 * awarded / possible;
            int score = (int) Math.clamp(Math.round(normalized - penalty), 1, 10);
            lines.add("Timing: " + timing.explanation() + " Deduction " + penalty + "/1.");
            if (biggestLoss == 0 && missingEvidence != null) nextStep = missingEvidence;
            if (penalty > 0 && biggestLoss * 10 / possible < 1) nextStep = timing.action();
            return new Result(score, "Meal quality " + score + "/10 from " + number(awarded) + "/" + number(possible)
                + " assessable points, scaled to " + number(normalized) + "/10 before timing.\n" + String.join("\n", lines)
                + "\nNext step: " + nextStep);
        }
    }
}
