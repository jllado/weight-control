package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;

public final class MoodDtos {

    private MoodDtos() {
    }

    public record MoodRequest(
        @NotNull LocalDate date,
        @NotNull MoodPeriod period,
        @NotNull @Min(1) @Max(5) Integer value,
        @Size(max = 500) String note
    ) {
    }

    public record CoachMoodRequest(
        @NotNull LocalDate date,
        @NotNull MoodPeriod period,
        @NotNull @Min(1) @Max(5) Integer value,
        @Size(max = 500) String note,
        @AssertTrue boolean confirmed
    ) {
        public MoodRequest mood() {
            return new MoodRequest(date, period, value, note);
        }
    }

    public record MoodResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        MoodPeriod period,
        Integer value,
        String note
    ) {
        public static MoodResponse from(Mood mood) {
            return new MoodResponse(
                mood.getId(),
                DateTimes.formatDate(mood.getMoodDate()),
                mood.getMoodDate(),
                mood.getPeriod(),
                mood.getValue(),
                mood.getNote()
            );
        }
    }
}
