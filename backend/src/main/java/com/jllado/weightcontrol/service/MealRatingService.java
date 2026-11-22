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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealRatingService {
    private static final int VERSION = 3;

    public static final String RULES = """
        Score the saved meal using its foods, portions and targets effective on the meal date. For main meals (default lunch/dinner), estimate plate volume from identifiable foods and consumed quantities: half non-starchy vegetables (2 points), one quarter protein and one quarter starch (0.5 each). Fruit does not count as vegetables. Known absence earns zero. Skip uncertain proportions. Main meal size earns 1 point through the configured share of the dated calorie target (default 65%), decreasing linearly to zero at 100%. Other meals skip plate and size.
        Protein and fiber each earn up to 1 point in proportion to the meal's calorie share of the dated daily gram goal. Salt uses the same calorie share of the daily limit (personal or 5 g); saturated fat uses the configured share of meal energy (default 10%, 9 kcal/g). Each limit earns 1 through the limit, decreasing linearly to zero at twice the limit. Skip missing targets, amounts and partial nutrient coverage. Normalize assessable criteria to 10. For every meal type, total earlier meals on the same date in start-time and ID order. Deduct up to 2 points each for calories and carbohydrates: 2 times the fraction of this meal's amount above the remaining daily budget (minimum remaining zero). Use the dated calorie plan and carbohydrate maximum. Skip an adjustment when its evidence is unknown.
        Deduct at most 1 point if the interval from previous meal end to this meal start is below the preference (default 90 minutes), or dinner/evening snack ends too close to usual bedtime (default 60 minutes). Use the median local bedtime of available preceding seven nights, treating times across midnight as adjacent. An evening snack follows dinner in the same evening or starts within four hours before the median bedtime or later that evening. Missing timing evidence is neutral. Round once to an integer from 1 to 10. Without assessable food or portion evidence, leave the score empty. Save the explanation and one next step with the result; adding, editing, moving or deleting earlier meals refreshes affected same-day automatic ratings.
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

    record Position(LocalDate date, LocalTime time, long id) {
        static Position of(Meal meal) { return new Position(meal.getMealDate(), meal.getMealTime(), meal.getId()); }

        boolean affects(Meal meal) {
            if (!date.equals(meal.getMealDate())) return false;
            return time == null || meal.getMealTime() == null || time.isBefore(meal.getMealTime())
                || (time.equals(meal.getMealTime()) && id <= meal.getId());
        }
    }

    void apply(User user, List<Meal> saved, Position... previous) {
        profiles.captureInitial(user);
        var changes = new ArrayList<>(List.of(previous));
        saved.stream().map(Position::of).forEach(changes::add);
        for (var date : changes.stream().map(Position::date).distinct().toList()) {
            var day = meals.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, date, date);
            var affected = day.stream().filter(meal -> saved.contains(meal)
                || (meal.getRatingVersion() != null && changes.stream().anyMatch(change -> change.affects(meal)))).toList();
            assessDate(user, date, day, affected, (meal, result) -> {
                meal.setRating(result.score());
                meal.setRatingExplanation(result.explanation());
                meal.setRatingVersion(VERSION);
            });
        }
    }

    @Transactional
    public RerateResult rerate(User authenticated, boolean apply) {
        var user = users.findByIdForUpdate(authenticated.getId()).orElseThrow();
        var eligible = meals.findLegacyAutomaticRatings(user);
        var byDate = eligible.stream().collect(Collectors.groupingBy(Meal::getMealDate, LinkedHashMap::new, Collectors.toList()));
        for (var batch : byDate.values()) {
            var date = batch.getFirst().getMealDate();
            assessDate(user, date, meals.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, date, date), batch, (meal, result) -> {
                if (apply) meals.migrateRating(user.getId(), meal.getId(), result.score(), result.explanation(), VERSION);
            });
        }
        // Every eligible score changes from V129's Base 5 explanation to the new version.
        return new RerateResult(eligible.size(), eligible.size(), apply);
    }

    record RatingChange(LocalTime time, Integer previous, Integer score) { }

    @Transactional
    public List<RatingChange> rerateDay(User authenticated, LocalDate date, boolean apply) {
        var user = users.findByIdForUpdate(authenticated.getId()).orElseThrow();
        var day = meals.findByUserAndMealDateBetweenOrderByMealDateAscIdAsc(user, date, date);
        var eligible = day.stream().filter(meal -> Integer.valueOf(2).equals(meal.getRatingVersion())).toList();
        var changes = new ArrayList<RatingChange>();
        assessDate(user, date, day, eligible, (meal, result) -> {
            changes.add(new RatingChange(meal.getMealTime(), meal.getRating(), result.score()));
            if (apply) meals.migrateRating(user.getId(), meal.getId(), result.score(), result.explanation(), VERSION);
        });
        return changes;
    }

    private void assessDate(User user, LocalDate date, List<Meal> day, List<Meal> batch, java.util.function.BiConsumer<Meal, Result> save) {
        if (batch.isEmpty()) return;
        var profile = profiles.find(user, date).orElse(new Targets(null, null, null, null, null));
        Integer saved = plans.savedCalories(user, date);
        var targets = new Targets(saved == null ? profile.weekdayCalories() : saved,
            profile.proteinGrams(), profile.fiberGrams(), profile.saltGrams(), profile.fatPercentage());
        var prefs = preferences.forDate(user, date);
        var history = new ArrayList<MealRepository.Timing>();
        var yesterday = date.minusDays(1);
        meals.findFirstTimingByUserAndMealDateLessThanAndMealTimeIsNotNullOrderByMealDateDescMealTimeDescIdDesc(user, yesterday).ifPresent(history::add);
        history.addAll(meals.findTimingByUserAndMealDateBetweenAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user, yesterday, date));
        // Sleep dates normally refer to waking. Filter again by the original local bedtime's evening.
        var nights = sleeps.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, date.minusDays(8), date);
        for (var meal : batch) {
            var timing = MealRatingTiming.evaluate(meal, prefs, history, nights);
            save.accept(meal, score(meal, targets, prefs, timing, MealBudget.before(meal, day)));
        }
    }

    record Result(Integer score, String explanation) { }

    static Result score(Meal meal, Targets targets, Preferences prefs, MealRatingTiming timing, MealBudget budget) {
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
        Integer kcalTarget = targets.weekdayCalories();
        BigDecimal carbTarget = kcalTarget == null ? null : NutrientTargetService.macroTargets(targets.proteinGrams(),
            targets.fatPercentage(), kcalTarget).carbohydrate().value();
        var calories = MealBudget.impact("calories", "kcal", kcalTarget == null ? null : BigDecimal.valueOf(kcalTarget),
            budget.earlierCalories(), BigDecimal.valueOf(meal.getCalories()));
        var carbs = MealBudget.impact("carbohydrates", "g", carbTarget, budget.earlierCarbs(), meal.getCarbohydrateGrams());
        return quality.finish(timing, calories, carbs);
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

        Result finish(MealRatingTiming timing, MealBudget.Impact calories, MealBudget.Impact carbs) {
            if (possible == 0) {
                lines.add("Daily budgets: no adjustment applied to an unrated meal.");
                lines.add("Timing: " + timing.explanation() + " No deduction applied.");
                return new Result(null, "Unrated: no assessable food or portion evidence.\n" + String.join("\n", lines)
                    + "\nNext step: Record identifiable foods and their portions.");
            }
            lines.add(calories.explanation());
            lines.add(carbs.explanation());
            int penalty = timing.missed() ? 1 : 0;
            double normalized = 10 * awarded / possible;
            int score = (int) Math.clamp(Math.round(normalized - calories.deduction() - carbs.deduction() - penalty), 1, 10);
            lines.add("Timing: " + timing.explanation() + " Deduction " + penalty + "/1.");
            if (biggestLoss == 0 && missingEvidence != null) nextStep = missingEvidence;
            double loss = biggestLoss * 10 / possible;
            if (penalty > loss) nextStep = timing.action();
            if (calories.deduction() > Math.max(loss, penalty)) nextStep = "Plan this portion around the calories left for the day.";
            if (carbs.deduction() > Math.max(Math.max(loss, penalty), calories.deduction())) {
                nextStep = "Adjust the carbohydrate portion to the grams left for the day.";
            }
            return new Result(score, "Meal quality " + score + "/10 from " + number(awarded) + "/" + number(possible)
                + " assessable points, scaled to " + number(normalized) + "/10 before daily-budget and timing deductions.\n" + String.join("\n", lines)
                + "\nNext step: " + nextStep);
        }
    }
}
