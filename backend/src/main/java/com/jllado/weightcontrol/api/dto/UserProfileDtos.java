package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.UserFitnessLevel;
import com.jllado.weightcontrol.domain.UserSex;
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
        @NotNull Boolean takesMedication
    ) {
    }

    public record UserProfileResponse(
        LocalDate birthDate,
        Integer heightCm,
        UserSex sex,
        UserFitnessLevel fitnessLevel,
        boolean takesMedication
    ) {
        public static UserProfileResponse from(User user) {
            return new UserProfileResponse(
                user.getBirthDate(),
                user.getHeightCm(),
                user.getSex(),
                user.getFitnessLevel(),
                user.isTakesMedication()
            );
        }
    }
}
