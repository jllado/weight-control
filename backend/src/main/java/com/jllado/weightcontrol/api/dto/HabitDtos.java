package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public final class HabitDtos {

    private HabitDtos() {
    }

    public record HabitRequest(@NotBlank String name, @NotNull Integer duration) {
    }

    public record HabitResponse(
        Long id,
        String startDateFormat,
        OffsetDateTime startDate,
        Integer duration,
        String lastTimeDateFormat,
        OffsetDateTime lastTimeDate,
        String name,
        Integer times,
        Integer currentStrike,
        Integer bestStrike
    ) {
        public static HabitResponse from(Habit habit) {
            return new HabitResponse(
                habit.getId(),
                DateTimes.formatDate(habit.getStartDate()),
                habit.getStartDate(),
                habit.getDuration(),
                habit.getLastTimeDate() == null ? null : DateTimes.formatDate(habit.getLastTimeDate()),
                habit.getLastTimeDate(),
                habit.getName(),
                habit.getTimes(),
                habit.getCurrentStrike(),
                habit.getBestStrike()
            );
        }
    }
}
