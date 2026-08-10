package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineType;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public final class RoutineDtos {

    private RoutineDtos() {
    }

    public record RoutineRequest(@NotBlank String name, @NotEmpty Set<RoutineType> types, LocalTime reminderTime) {
    }

    public record RoutineCheckinRequest(@NotNull OffsetDateTime date) {
    }

    public record RoutineResponse(
        Long id,
        String startDateFormat,
        OffsetDateTime startDate,
        String lastTimeDateFormat,
        OffsetDateTime lastTimeDate,
        String name,
        LocalTime reminderTime,
        Integer currentStrike,
        Integer bestStrike,
        Set<RoutineType> types,
        List<OffsetDateTime> times
    ) {
        public static RoutineResponse from(Routine routine, List<OffsetDateTime> times) {
            return new RoutineResponse(
                routine.getId(),
                DateTimes.formatDate(routine.getStartDate()),
                routine.getStartDate(),
                routine.getLastTimeDate() == null ? null : DateTimes.formatDate(routine.getLastTimeDate()),
                routine.getLastTimeDate(),
                routine.getName(),
                routine.getReminderTime(),
                routine.getCurrentStrike(),
                routine.getBestStrike(),
                routine.getTypes(),
                times
            );
        }
    }
}
