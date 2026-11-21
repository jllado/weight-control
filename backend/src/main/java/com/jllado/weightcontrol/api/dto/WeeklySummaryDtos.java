package com.jllado.weightcontrol.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jllado.weightcontrol.service.WeeklySummarySnapshot;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class WeeklySummaryDtos {

    private WeeklySummaryDtos() {
    }

    public record WeeklySummaryScheduleRequest(
        @NotNull DayOfWeek deliveryDay,
        @NotNull LocalTime deliveryTime,
        boolean reminderEnabled,
        @NotNull DayOfWeek reminderDay,
        @NotNull LocalTime reminderTime
    ) {
    }

    public record WeeklySummaryConfigResponse(
        boolean enabled,
        boolean canSend,
        String recipientEmail,
        DayOfWeek deliveryDay,
        LocalTime deliveryTime,
        boolean reminderEnabled,
        DayOfWeek reminderDay,
        LocalTime reminderTime,
        String timeZone
    ) {
    }

    public record WeeklySummaryArchiveResponse(
        LocalDate latestEligibleFriday,
        boolean actionConfigured,
        List<WeeklySummaryListItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public LocalDate latestEligiblePeriodEnd() { return latestEligibleFriday; }
    }

    public interface ReportPeriod {
        LocalDate periodStart();
        LocalDate fridayDate();

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        default LocalDate periodEnd() { return fridayDate(); }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        default boolean transition() { return periodEnd().isBefore(periodStart().plusDays(6)); }
    }

    public record WeeklySummaryListItem(
        LocalDate periodStart,
        LocalDate fridayDate,
        Instant createdAt,
        boolean reflectionSaved
    ) implements ReportPeriod {
    }

    public record WeeklySummaryPreviewResponse(
        LocalDate periodStart,
        LocalDate fridayDate,
        boolean canCreate,
        boolean alreadySaved,
        WeeklySummarySnapshot snapshot
    ) implements ReportPeriod {
    }

    public record WeeklySummaryDetailResponse(
        LocalDate periodStart,
        LocalDate fridayDate,
        Instant createdAt,
        WeeklySummarySnapshot snapshot,
        WeeklyReflectionDtos.WeeklyReflectionResponse reflection,
        com.jllado.weightcontrol.service.WeeklySummaryPresentationMapper.Presentation presentation
    ) implements ReportPeriod {
        public WeeklySummaryDetailResponse(LocalDate periodStart, LocalDate fridayDate, Instant createdAt,
            WeeklySummarySnapshot snapshot, WeeklyReflectionDtos.WeeklyReflectionResponse reflection) {
            this(periodStart, fridayDate, createdAt, snapshot, reflection, null);
        }
    }
}
