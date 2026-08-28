package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.api.dto.DashboardDtos.DashboardResponse;
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
        @NotNull List<@NotNull LocalTime> reminderTimes,
        @NotNull Boolean personalRecordsEnabled
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
        Boolean personalRecordsEnabled,
        Set<RoutineType> types,
        List<OffsetDateTime> times
    ) {
        public static RoutineResponse from(Routine routine, List<OffsetDateTime> times) {
            RoutineSummaryResponse summary = RoutineSummaryResponse.from(routine);
            return new RoutineResponse(
                summary.id(),
                summary.startDateFormat(),
                summary.startDate(),
                summary.lastTimeDateFormat(),
                summary.lastTimeDate(),
                summary.name(),
                summary.reminders(),
                summary.currentStrike(),
                summary.bestStrike(),
                summary.personalRecordsEnabled(),
                summary.types(),
                times
            );
        }
    }

    public record RoutineSummaryResponse(
        Long id,
        String startDateFormat,
        OffsetDateTime startDate,
        String lastTimeDateFormat,
        OffsetDateTime lastTimeDate,
        String name,
        List<RoutineReminderResponse> reminders,
        Integer currentStrike,
        Integer bestStrike,
        Boolean personalRecordsEnabled,
        Set<RoutineType> types
    ) {
        public static RoutineSummaryResponse from(Routine routine) {
            return new RoutineSummaryResponse(
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
                routine.getPersonalRecordsEnabled(),
                routine.getTypes()
            );
        }
    }

    public record RoutineCheckinMutationResponse(
        RoutineSummaryResponse routine,
        OffsetDateTime checkedAt,
        boolean changed,
        DashboardResponse dashboard
    ) {
    }
}
