package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.ProgressPhotoSide;
import com.jllado.weightcontrol.domain.Weight;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ProgressPhotoDtos {

    private ProgressPhotoDtos() {
    }

    public record ProgressPhotoSetResponse(
        Long photoSetId,
        LocalDate date,
        BigDecimal weightKg,
        BigDecimal fatPercentage,
        BigDecimal fatMassKg,
        BigDecimal muscleMassKg,
        BigDecimal musclePercentage,
        List<ProgressPhotoSide> availableSides
    ) {
        public static ProgressPhotoSetResponse from(Weight weight) {
            return new ProgressPhotoSetResponse(
                weight.getId(),
                DateTimes.toLocalDate(weight.getMeasuredAt()),
                weight.getWeight(),
                weight.getFatPercentage(),
                weight.getFat(),
                weight.getMuscle(),
                weight.getMusclePercentage(),
                java.util.Arrays.stream(ProgressPhotoSide.values())
                    .filter(side -> side.path(weight) != null)
                    .toList()
            );
        }
    }

    public record OpenAiFileResponse(List<String> openaiFileResponse) {
    }
}
