package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineReminder;
import com.jllado.weightcontrol.domain.RoutineType;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class RoutineDtos {

    private RoutineDtos() {
    }

    public record RoutineRequest(
        @NotBlank String name,
        @NotEmpty Set<RoutineType> types,
        @NotNull List<@NotNull LocalTime> reminderTimes
    ) {
    }

    public record RoutineReminderResponse(Long id, LocalTime time) {
        public static RoutineReminderResponse from(RoutineReminder reminder) {
            return new RoutineReminderResponse(reminder.getId(), reminder.getReminderTime());
        }
    }

    public record RoutineCheckinRequest(@NotNull OffsetDateTime date) {
    }

    public record RoutineReminderSnoozeRequest(@NotNull Integer minutes) {
    }

    public record RoutineReminderSnoozeResponse(OffsetDateTime nextReminderAt) {
    }

    public record RoutineResponse(
        Long id,
        String startDateFormat,
        OffsetDateTime startDate,
        String lastTimeDateFormat,
        OffsetDateTime lastTimeDate,
        String name,
        List<RoutineReminderResponse> reminders,
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
                routine.getReminders().stream()
                    .sorted(Comparator.comparing(RoutineReminder::getReminderTime))
                    .map(RoutineReminderResponse::from)
                    .toList(),
                routine.getCurrentStrike(),
                routine.getBestStrike(),
                routine.getTypes(),
                times
            );
        }
    }
}
