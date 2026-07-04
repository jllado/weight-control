package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.UserFitnessLevel;
import com.jllado.weightcontrol.domain.UserSex;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public final class UserProfileDtos {

    private UserProfileDtos() {
    }

    public record UserProfileRequest(
        LocalDate birthDate,
        @Min(1) Integer heightCm,
        UserSex sex,
        UserFitnessLevel fitnessLevel,
        @NotNull Boolean takesMedication,
        @NotNull @Valid TypicalCaloriesPerDayRequest typicalCaloriesPerDay
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

    public record UserProfileResponse(
        LocalDate birthDate,
        Integer heightCm,
        UserSex sex,
        UserFitnessLevel fitnessLevel,
        boolean takesMedication,
        TypicalCaloriesPerDayResponse typicalCaloriesPerDay
    ) {
        public static UserProfileResponse from(User user) {
            return new UserProfileResponse(
                user.getBirthDate(),
                user.getHeightCm(),
                user.getSex(),
                user.getFitnessLevel(),
                user.isTakesMedication(),
                new TypicalCaloriesPerDayResponse(
                    user.getTypicalCaloriesSaturday(),
                    user.getTypicalCaloriesSunday(),
                    user.getTypicalCaloriesMonday(),
                    user.getTypicalCaloriesTuesday(),
                    user.getTypicalCaloriesWednesday(),
                    user.getTypicalCaloriesThursday(),
                    user.getTypicalCaloriesFriday()
                )
            );
        }
    }

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
}
