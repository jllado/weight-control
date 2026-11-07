package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;

public final class SleepDtos {

    private SleepDtos() {
    }

    public record SleepRequest(
        @NotNull LocalDate sleepDate,
        @NotNull @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime bedtimeStart,
        @NotNull @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime bedtimeEnd,
        @NotNull @DecimalMin("0") Integer totalSleepDuration,
        @NotNull @DecimalMin("0") Integer deepSleepDuration,
        @NotNull @DecimalMin("0") Integer remSleepDuration,
        @NotNull @DecimalMin("0") Integer lightSleepDuration,
        @NotNull @DecimalMin("0") Integer awakeTime,
        @NotNull @DecimalMin("0.0") BigDecimal averageHeartRate,
        @NotNull @DecimalMin("0") Integer averageHrv
    ) {
    }

    public record CoachSleepRequest(
        @NotNull LocalDate sleepDate,
        @NotNull @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime bedtimeStart,
        @NotNull @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE) OffsetDateTime bedtimeEnd,
        @NotNull @DecimalMin("0") Integer totalSleepDuration,
        @NotNull @DecimalMin("0") Integer deepSleepDuration,
        @NotNull @DecimalMin("0") Integer remSleepDuration,
        @NotNull @DecimalMin("0") Integer lightSleepDuration,
        @NotNull @DecimalMin("0") Integer awakeTime,
        @NotNull @DecimalMin("0.0") BigDecimal averageHeartRate,
        @NotNull @DecimalMin("0") Integer averageHrv,
        @AssertTrue boolean confirmed
    ) {
        public SleepRequest sleep() {
            return new SleepRequest(sleepDate, bedtimeStart, bedtimeEnd, totalSleepDuration, deepSleepDuration, remSleepDuration,
                lightSleepDuration, awakeTime, averageHeartRate, averageHrv);
        }
    }

    public record SleepResponse(
        Long id,
        String dateFormat,
        String bedtimeStartFormat,
        String bedtimeEndFormat,
        String date,
        OffsetDateTime bedtimeStart,
        OffsetDateTime bedtimeEnd,
        Integer bedtimeStartOffsetSeconds,
        Integer bedtimeEndOffsetSeconds,
        Integer totalSleepDuration,
        Integer awakeTime,
        Integer deepSleepDuration,
        Integer remSleepDuration,
        Integer lightSleepDuration,
        BigDecimal averageHeartRate,
        Integer averageHrv
    ) {
        public static SleepResponse from(Sleep sleep) {
            return new SleepResponse(
                sleep.getId(),
                DateTimes.formatDate(sleep.getSleepDate()),
                sleep.getBedtimeStart() == null ? null : DateTimes.DATE_TIME_FORMAT.format(sleep.getBedtimeStart()),
                sleep.getBedtimeEnd() == null ? null : DateTimes.DATE_TIME_FORMAT.format(sleep.getBedtimeEnd()),
                sleep.getSleepDate().toString(),
                sleep.getBedtimeStart(),
                sleep.getBedtimeEnd(),
                sleep.getBedtimeStart() == null ? null : sleep.getBedtimeStart().getOffset().getTotalSeconds(),
                sleep.getBedtimeEnd() == null ? null : sleep.getBedtimeEnd().getOffset().getTotalSeconds(),
                sleep.getTotalSleepDuration(),
                sleep.getAwakeTime(),
                sleep.getDeepSleepDuration(),
                sleep.getRemSleepDuration(),
                sleep.getLightSleepDuration(),
                sleep.getAverageHeartRate(),
                sleep.getAverageHrv()
            );
        }
    }
}
