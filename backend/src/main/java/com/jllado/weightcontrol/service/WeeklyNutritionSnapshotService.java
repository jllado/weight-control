package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.NutritionDtos.*;
import com.jllado.weightcontrol.domain.MacroTargetComparison;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.service.NutritionService.DailyNutritionSummary;
import com.jllado.weightcontrol.service.WeeklyNutritionSnapshot.*;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class WeeklyNutritionSnapshotService {
    private final NutritionService nutrition;
    private final WeeklyCaloriePlanService plans;
    private final NutrientTargetService targets;
    public WeeklyNutritionSnapshotService(NutritionService nutrition, WeeklyCaloriePlanService plans, NutrientTargetService targets) {
        this.nutrition = nutrition; this.plans = plans; this.targets = targets;
    }

    public WeeklyNutritionSnapshot capture(User user, LocalDate friday) {
        var capturedTargets = targets.resolve(user, LocalDate.now(DateTimes.USER_ZONE));
        return new WeeklyNutritionSnapshot(Instant.now(), "Current targets captured when saved", capturedTargets,
            period(user, friday, capturedTargets), period(user, friday.minusWeeks(1), capturedTargets),
            period(user, friday.minusWeeks(52), capturedTargets));
    }

    private Period period(User user, LocalDate end, NutrientTargetsResponse capturedTargets) {
        LocalDate start = end.minusDays(6);
        var days = nutrition.findBetween(user, start, end);
        var datedTargets = plans.effectiveTargets(user, start, end).stream()
            .map(target -> new DatedTarget(target.date(), targets.macroTargetsUnrounded(user, target.calories()), user.getMacroFatTargetPercentage())).toList();
        Map<LocalDate, DatedTarget> byDate = datedTargets.stream().collect(Collectors.toMap(DatedTarget::date, Function.identity()));
        List<Measure> measures = new ArrayList<>();
        measures.add(macro("protein", "Protein", days, DailyNutritionSummary::proteinGrams, byDate, MacroTargetsResponse::protein));
        measures.add(macro("carbohydrate", "Carbohydrates", days, DailyNutritionSummary::carbohydrateGrams, byDate, MacroTargetsResponse::carbohydrate));
        measures.add(macro("fat", "Fat", days, DailyNutritionSummary::fatGrams, byDate, MacroTargetsResponse::fat));
        measures.add(nutrient("vitaminD", "Vitamin D", "µg", days, NutrientSummary::vitaminDMicrograms, NutrientSummary::vitaminDCoverage, capturedTargets.vitaminD()));
        measures.add(nutrient("omega3", "Total omega-3", "mg", days, NutrientSummary::omega3Milligrams, NutrientSummary::omega3Coverage, capturedTargets.omega3()));
        measures.add(nutrient("epaDha", "EPA+DHA", "mg", days, NutrientSummary::epaDhaMilligrams, NutrientSummary::epaDhaCoverage, capturedTargets.epaDha()));
        measures.add(nutrient("ala", "ALA", "mg", days, NutrientSummary::alaMilligrams, NutrientSummary::alaCoverage, capturedTargets.ala()));
        measures.add(nutrient("fiber", "Fiber", "g", days, NutrientSummary::fiberGrams, NutrientSummary::fiberCoverage, capturedTargets.fiber()));
        measures.add(nutrient("magnesium", "Magnesium", "mg", days, NutrientSummary::magnesiumMilligrams, NutrientSummary::magnesiumCoverage, capturedTargets.magnesium()));
        int completed = (int) start.datesUntil(end.plusDays(1)).filter(date -> user.getLastCompletedDashboardDate() != null
            && !date.isAfter(user.getLastCompletedDashboardDate())).count();
        return new Period(start, end, completed, days.size(), List.copyOf(days), datedTargets, List.copyOf(measures));
    }

    private Measure macro(String key, String label, List<DailyNutritionSummary> days,
        Function<DailyNutritionSummary, BigDecimal> value, Map<LocalDate, DatedTarget> byDate,
        Function<MacroTargetsResponse, MacroTargetValue> target) {
        var eligible = days.stream().filter(day -> day.completed() && day.macrosComplete()).toList();
        var allocations = eligible.stream().map(day -> target.apply(byDate.get(day.date()).macroTargets())).toList();
        boolean completeTargets = !allocations.isEmpty() && allocations.stream().allMatch(item -> item.value() != null);
        String unavailable = allocations.stream().map(MacroTargetValue::unavailableReason).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return new Measure(key, label, "g", mean(eligible.stream().map(value).toList()), eligible.size(),
            (int) days.stream().filter(day -> value.apply(day) != null).count(), 0, 0, 0,
            completeTargets ? meanTarget(key, eligible, byDate, allocations) : null,
            key.equals("protein") ? MacroTargetComparison.MINIMUM : MacroTargetComparison.MAXIMUM, unavailable);
    }

    private BigDecimal meanTarget(String key, List<DailyNutritionSummary> eligible,
        Map<LocalDate, DatedTarget> byDate, List<MacroTargetValue> allocations) {
        if (!key.equals("fat")) return mean(allocations.stream().map(MacroTargetValue::value).toList());
        return eligible.stream().map(day -> byDate.get(day.date()))
            .map(target -> BigDecimal.valueOf(target.macroTargets().calorieBudget()).multiply(target.fatPercentage()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(900L * eligible.size()), 2, RoundingMode.HALF_UP);
    }

    private Measure nutrient(String key, String label, String unit, List<DailyNutritionSummary> days,
        Function<NutrientSummary, BigDecimal> value, Function<NutrientSummary, NutrientCoverage> coverage,
        NutrientTargetValue target) {
        var recorded = days.stream().map(DailyNutritionSummary::nutrients).toList();
        var eligible = days.stream().filter(DailyNutritionSummary::completed).map(DailyNutritionSummary::nutrients)
            .filter(summary -> summary.totalFoods() > 0 && summary.mealsWithoutFoods() == 0
                && coverage.apply(summary).foodsWithValues() == summary.totalFoods()).toList();
        return new Measure(key, label, unit, mean(eligible.stream().map(value).toList()), eligible.size(),
            (int) recorded.stream().filter(summary -> value.apply(summary) != null).count(),
            recorded.stream().mapToInt(summary -> coverage.apply(summary).foodsWithValues()).sum(),
            recorded.stream().mapToInt(NutrientSummary::totalFoods).sum(),
            recorded.stream().mapToInt(summary -> coverage.apply(summary).estimatedFoods()).sum(),
            target.value(), MacroTargetComparison.MINIMUM, null);
    }

    private BigDecimal mean(List<BigDecimal> values) {
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
