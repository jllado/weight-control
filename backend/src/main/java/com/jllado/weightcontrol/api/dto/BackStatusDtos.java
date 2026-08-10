package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.BackStatus;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class BackStatusDtos {

    private BackStatusDtos() {
    }

    public record BackRegionStatus(
        @NotNull @Min(0) @Max(10) Integer pain,
        @NotNull @Min(0) @Max(10) Integer stiffness,
        @NotNull @Min(0) @Max(10) Integer activityLimitation
    ) {
    }

    public record BackStatusRequest(
        @NotNull LocalDate date,
        @Valid @NotNull BackRegionStatus lower,
        @Valid @NotNull BackRegionStatus middle,
        @Valid @NotNull BackRegionStatus upper,
        @Size(max = 500) String note
    ) {
    }

    public record BackStatusResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        BackRegionStatus lower,
        BackRegionStatus middle,
        BackRegionStatus upper,
        String note
    ) {
        public static BackStatusResponse from(BackStatus status) {
            return new BackStatusResponse(
                status.getId(),
                DateTimes.formatDate(status.getStatusDate()),
                status.getStatusDate(),
                new BackRegionStatus(status.getLowerPain(), status.getLowerStiffness(), status.getLowerActivityLimitation()),
                new BackRegionStatus(status.getMiddlePain(), status.getMiddleStiffness(), status.getMiddleActivityLimitation()),
                new BackRegionStatus(status.getUpperPain(), status.getUpperStiffness(), status.getUpperActivityLimitation()),
                status.getNote()
            );
        }
    }
}
