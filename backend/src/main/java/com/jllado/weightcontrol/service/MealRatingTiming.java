package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MealRatingDtos.Preferences;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.MealType;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.repository.MealRepository.Timing;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

record MealRatingTiming(boolean missed, String explanation, String action) {
    // An evening continues past midnight. The noon boundary keeps 23:55 and 00:05 ten minutes apart.
    static LocalDate evening(LocalDateTime time) {
        return time.minusHours(12).toLocalDate();
    }

    static LocalDateTime medianBedtime(LocalDate evening, List<Sleep> sleeps) {
        var minutes = sleeps.stream().map(Sleep::getBedtimeStart).filter(java.util.Objects::nonNull)
            .map(time -> time.toLocalDateTime())
            .filter(time -> !evening(time).isBefore(evening.minusDays(7)) && evening(time).isBefore(evening))
            .map(time -> Duration.between(evening(time).atTime(LocalTime.NOON), time).toMinutes()).sorted().toList();
        if (minutes.isEmpty()) return null;
        int middle = minutes.size() / 2;
        long median = (minutes.get(middle) + minutes.get((minutes.size() - 1) / 2)) / 2;
        return evening.atTime(LocalTime.NOON).plusMinutes(median);
    }

    static MealRatingTiming evaluate(Meal meal, Preferences prefs, List<Timing> ordered, List<Sleep> sleeps) {
        if (meal.getMealTime() == null) return new MealRatingTiming(false, "Timing unavailable (start time missing).", null);
        var start = meal.getMealDate().atTime(meal.getMealTime());
        var evening = evening(start);
        List<String> evidence = new ArrayList<>();
        var earlier = ordered.stream().filter(entry -> entry.start().isBefore(start)
            || (entry.start().equals(start) && entry.id() < meal.getId())).toList();
        var previous = earlier.isEmpty() ? null : earlier.getLast();
        boolean spacingMissed = false;
        boolean bedtimeMissed = false;
        if (previous != null && previous.durationMinutes() != null) {
            long gap = Duration.between(previous.start().plusMinutes(previous.durationMinutes()), start).toMinutes();
            spacingMissed = prefs.minimumMealGapMinutes() > 0 && gap < prefs.minimumMealGapMinutes();
            evidence.add("Previous meal end to start " + gap + " min (goal " + prefs.minimumMealGapMinutes() + " min)");
        } else {
            evidence.add("Previous meal gap unavailable");
        }
        if (meal.getMealType() == MealType.DINNER || meal.getMealType() == MealType.SNACK) {
            var bedtime = medianBedtime(evening, sleeps);
            boolean afterDinner = earlier.stream().anyMatch(entry -> entry.mealType() == MealType.DINNER && evening(entry.start()).equals(evening));
            boolean eveningMeal = meal.getMealType() == MealType.DINNER || afterDinner
                || (bedtime != null && !start.isBefore(bedtime.minusHours(4)));
            if (eveningMeal && bedtime != null && meal.getDurationMinutes() != null) {
                var end = start.plusMinutes(meal.getDurationMinutes());
                long gap = Duration.between(end, bedtime).toMinutes();
                bedtimeMissed = prefs.minimumBedtimeGapMinutes() > 0 && gap < prefs.minimumBedtimeGapMinutes();
                evidence.add("Meal end " + end.toLocalTime() + ", usual bedtime " + bedtime.toLocalTime()
                    + ", gap " + gap + " min (goal " + prefs.minimumBedtimeGapMinutes() + " min)");
            } else if (eveningMeal || bedtime == null) {
                evidence.add("Bedtime gap unavailable");
            }
        }
        String action = null;
        if (spacingMissed && bedtimeMissed) action = "Move the late food into the preceding meal so the evening finishes earlier.";
        else if (spacingMissed) action = "Leave at least " + prefs.minimumMealGapMinutes() + " minutes after the previous meal ends.";
        else if (bedtimeMissed) action = "Finish this meal at least " + prefs.minimumBedtimeGapMinutes() + " minutes before your usual bedtime.";
        return new MealRatingTiming(spacingMissed || bedtimeMissed, String.join(". ", evidence) + ".", action);
    }
}
