package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.Meal;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

record MealBudget(BigDecimal earlierCalories, BigDecimal earlierCarbs) {
    static MealBudget before(Meal meal, List<Meal> day) {
        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        for (var other : day) {
            if (other.getId().equals(meal.getId())) continue;
            if (other.getMealTime() == null || meal.getMealTime() == null) return new MealBudget(null, null);
            int order = other.getMealTime().compareTo(meal.getMealTime());
            if (order > 0 || (order == 0 && other.getId() > meal.getId())) continue;
            calories = calories.add(BigDecimal.valueOf(other.getCalories()));
            carbs = carbs == null || other.getCarbohydrateGrams() == null ? null : carbs.add(other.getCarbohydrateGrams());
        }
        return new MealBudget(calories, carbs);
    }

    record Impact(double deduction, String explanation) { }

    static Impact impact(String name, String unit, BigDecimal target, BigDecimal earlier, BigDecimal amount) {
        String label = "Daily " + name + ": ";
        if (target == null || earlier == null || amount == null) {
            return new Impact(0, label + "skipped (daily target, meal order or recorded amount unavailable).");
        }
        var remaining = target.subtract(earlier).max(BigDecimal.ZERO);
        var excess = amount.subtract(remaining).max(BigDecimal.ZERO);
        double deduction = excess.signum() == 0 ? 0 : excess.divide(amount, MathContext.DECIMAL128).doubleValue() * 2;
        return new Impact(deduction, label + fmt(earlier) + " / " + fmt(target) + " " + unit + " before this meal, "
            + fmt(remaining) + " left, meal " + fmt(amount) + ", " + fmt(excess) + " " + unit + " of this meal over budget. Deduction "
            + fmt(BigDecimal.valueOf(deduction)) + "/2.");
    }

    private static String fmt(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
