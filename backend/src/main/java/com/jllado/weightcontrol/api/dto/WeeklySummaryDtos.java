package com.jllado.weightcontrol.api.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public final class WeeklySummaryDtos {

    private WeeklySummaryDtos() {
    }

    public record WeeklySummaryConfigResponse(
        boolean enabled,
        String recipientEmail,
        DayOfWeek deliveryDay,
        LocalTime deliveryTime,
        String timeZone
    ) {
    }
}
