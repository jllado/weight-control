package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.service.WeeklySummarySnapshot;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class WeeklySummaryDtos {

    private WeeklySummaryDtos() {
    }

    public record WeeklySummaryConfigResponse(
        boolean enabled,
        boolean canSend,
        String recipientEmail,
        DayOfWeek deliveryDay,
        LocalTime deliveryTime,
        String timeZone
    ) {
    }

    public record WeeklySummaryArchiveResponse(
        LocalDate latestEligibleFriday,
        boolean actionConfigured,
        List<WeeklySummaryListItem> summaries
    ) {
    }

    public record WeeklySummaryListItem(
        LocalDate periodStart,
        LocalDate fridayDate,
        Instant createdAt,
        boolean reflectionSaved
    ) {
    }

    public record WeeklySummaryPreviewResponse(
        LocalDate periodStart,
        LocalDate fridayDate,
        boolean canCreate,
        boolean alreadySaved,
        WeeklySummarySnapshot snapshot
    ) {
    }

    public record WeeklySummaryDetailResponse(
        LocalDate periodStart,
        LocalDate fridayDate,
        Instant createdAt,
        WeeklySummarySnapshot snapshot,
        WeeklyReflectionDtos.WeeklyReflectionResponse reflection
    ) {
    }
}
