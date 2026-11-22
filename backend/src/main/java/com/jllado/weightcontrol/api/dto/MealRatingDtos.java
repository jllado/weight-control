package com.jllado.weightcontrol.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.jllado.weightcontrol.api.dto.MealDtos.WholeNumberDeserializer;
import com.jllado.weightcontrol.domain.MealType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public final class MealRatingDtos {
    private MealRatingDtos() { }

    public record Preferences(
        @NotNull Set<@NotNull MealType> mainMeals,
        @NotNull @Min(1) @Max(99) @JsonDeserialize(using = WholeNumberDeserializer.class) Integer mainMealLimitPercent,
        @NotNull @Min(0) @Max(1440) @JsonDeserialize(using = WholeNumberDeserializer.class) Integer minimumMealGapMinutes,
        @NotNull @Min(0) @Max(1440) @JsonDeserialize(using = WholeNumberDeserializer.class) Integer minimumBedtimeGapMinutes,
        @NotNull @Min(1) @Max(100) @JsonDeserialize(using = WholeNumberDeserializer.class) Integer saturatedFatEnergyPercent
    ) {
        public static Preferences defaults() {
            return new Preferences(java.util.EnumSet.of(MealType.LUNCH, MealType.DINNER), 65, 90, 60, 10);
        }
    }

    public record RerateResult(int eligible, int changed, boolean applied) { }
}
