package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.FastingPeriodSource;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public final class FastingPeriodDtos {

    private FastingPeriodDtos() {
    }

    public record FastingPeriodRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        String notes
    ) {
    }

    public record CoachFastingPeriodRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        String notes,
        @AssertTrue boolean confirmed
    ) {
        public FastingPeriodRequest period() {
            return new FastingPeriodRequest(startTime, endTime, notes);
        }
    }

    public record FastingPeriodResponse(
        Long id,
        String startTimeFormat,
        String endTimeFormat,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String notes,
        FastingPeriodSource source
    ) {
        public static FastingPeriodResponse from(FastingPeriod period) {
            return new FastingPeriodResponse(
                period.getId(),
                DateTimes.formatDateTime(period.getStartTime()),
                period.getEndTime() == null ? null : DateTimes.formatDateTime(period.getEndTime()),
                period.getStartTime(),
                period.getEndTime(),
                period.getNotes(),
                period.getSource()
            );
        }
    }
}
