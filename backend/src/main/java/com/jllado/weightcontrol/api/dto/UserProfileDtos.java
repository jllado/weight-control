package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.UserFitnessLevel;
import com.jllado.weightcontrol.domain.UserSex;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public final class UserProfileDtos {

    private UserProfileDtos() {
    }

    public record UserProfileRequest(
        LocalDate birthDate,
        @Min(1) Integer heightCm,
        UserSex sex,
        UserFitnessLevel fitnessLevel,
        @NotNull Boolean takesMedication,
        @NotNull @Min(0) Integer weeklyAverageCalorieMaximum,
        @NotNull @Valid TypicalCaloriesPerDayRequest typicalCaloriesPerDay,
        @NotNull @Valid CalorieShortcutsRequest calorieShortcuts,
        @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2) BigDecimal inShapeBodyFatLimit,
        DayOfWeek firstDayOfPlanningWeek,
        LocalDate expectedPlanningWeekEffectiveDate
    ) {
    }

    public record TypicalCaloriesPerDayRequest(
        @NotNull @Min(0) Integer saturday,
        @NotNull @Min(0) Integer sunday,
        @NotNull @Min(0) Integer monday,
        @NotNull @Min(0) Integer tuesday,
        @NotNull @Min(0) Integer wednesday,
        @NotNull @Min(0) Integer thursday,
        @NotNull @Min(0) Integer friday
    ) {
    }

    public record CalorieShortcutsRequest(
        @NotNull @Min(0) Integer onPlan,
        @NotNull @Min(0) Integer flexible,
        @NotNull @Min(0) Integer offPlan,
        @NotNull @Min(0) Integer binge
    ) {
    }

    public record UserProfileResponse(
        LocalDate birthDate,
        Integer heightCm,
        UserSex sex,
        UserFitnessLevel fitnessLevel,
        boolean takesMedication,
        int weeklyAverageCalorieMaximum,
        TypicalCaloriesPerDayResponse typicalCaloriesPerDay,
        CalorieShortcutsResponse calorieShortcuts,
        BigDecimal inShapeBodyFatLimit,
        List<PlanningWeekChangeResponse> planningWeekChanges
    ) {
        public static UserProfileResponse from(User user) {
            return new UserProfileResponse(
                user.getBirthDate(),
                user.getHeightCm(),
                user.getSex(),
                user.getFitnessLevel(),
                user.isTakesMedication(),
                user.getWeeklyAverageCalorieMaximum(),
                new TypicalCaloriesPerDayResponse(
                    user.getTypicalCaloriesSaturday(),
                    user.getTypicalCaloriesSunday(),
                    user.getTypicalCaloriesMonday(),
                    user.getTypicalCaloriesTuesday(),
                    user.getTypicalCaloriesWednesday(),
                    user.getTypicalCaloriesThursday(),
                    user.getTypicalCaloriesFriday()
                ),
                new CalorieShortcutsResponse(
                    user.getCalorieShortcutOnPlan(),
                    user.getCalorieShortcutFlexible(),
                    user.getCalorieShortcutOffPlan(),
                    user.getCalorieShortcutBinge()
                ),
                user.getInShapeBodyFatLimit(),
                user.getPlanningWeekChanges().stream().map(change -> {
                    LocalDate date = change.getEffectiveDate();
                    return new PlanningWeekChangeResponse(date, date.getDayOfWeek());
                }).toList()
            );
        }
    }

    public record PlanningWeekChangeResponse(LocalDate effectiveDate, DayOfWeek firstDay) { }

    public record TypicalCaloriesPerDayResponse(
        int saturday,
        int sunday,
        int monday,
        int tuesday,
        int wednesday,
        int thursday,
        int friday
    ) {
    }

    public record CalorieShortcutsResponse(
        int onPlan,
        int flexible,
        int offPlan,
        int binge
    ) {
    }
}
