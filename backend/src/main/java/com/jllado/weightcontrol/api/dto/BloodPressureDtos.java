package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.BloodPressure;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import java.time.OffsetDateTime;

public final class BloodPressureDtos {

    private BloodPressureDtos() {
    }

    public record BloodPressureRequest(
        @NotNull OffsetDateTime date,
        @NotNull Integer upper,
        @NotNull Integer lower
    ) {
    }

    public record CoachBloodPressureRequest(
        @NotNull OffsetDateTime date,
        @NotNull Integer upper,
        @NotNull Integer lower,
        @AssertTrue boolean confirmed
    ) {
        public BloodPressureRequest bloodPressure() {
            return new BloodPressureRequest(date, upper, lower);
        }
    }

    public record BloodPressureResponse(
        Long id,
        String dateFormat,
        OffsetDateTime date,
        Integer upper,
        Integer lower,
        Integer lostUpper,
        Integer lostLower
    ) {
        public static BloodPressureResponse from(BloodPressure bloodPressure) {
            return new BloodPressureResponse(
                bloodPressure.getId(),
                DateTimes.formatDateTime(bloodPressure.getMeasuredAt()),
                bloodPressure.getMeasuredAt(),
                bloodPressure.getUpper(),
                bloodPressure.getLower(),
                bloodPressure.getLostUpper(),
                bloodPressure.getLostLower()
            );
        }
    }
}
