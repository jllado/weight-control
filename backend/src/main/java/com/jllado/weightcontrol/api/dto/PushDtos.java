package com.jllado.weightcontrol.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class PushDtos {

    private PushDtos() {
    }

    public record PushKeysRequest(@NotBlank String p256dh, @NotBlank String auth) {
    }

    public record PushSubscriptionRequest(@NotBlank String endpoint, @NotNull @Valid PushKeysRequest keys) {
    }

    public record PushEndpointRequest(@NotBlank String endpoint) {
    }

    public record ReleaseNotificationRequest(
        @NotBlank @Pattern(regexp = "[0-9a-f]{40}") String commitSha,
        @NotBlank @Size(max = 80) String featureName
    ) {
    }

    public record PushConfigResponse(boolean enabled, String publicKey, String timeZone) {
    }

    public record ReminderSettingsRequest(
        @NotNull LocalTime morningTime,
        @NotNull LocalTime middayTime,
        @NotNull LocalTime eveningTime
    ) {
    }

    public record ReminderSettingsResponse(
        LocalTime morningTime,
        LocalTime middayTime,
        LocalTime eveningTime,
        String timeZone
    ) {
    }

    public enum AgendaEntryType {
        MOOD,
        BACK_PAIN,
        WEIGHT,
        BLOOD_PRESSURE,
        ROUTINE,
        MEDICATION
    }

    public enum AgendaEntryStatus {
        COMPLETED,
        PENDING,
        MISSED,
        RECORDED,
        NO_ISSUE
    }

    public record AgendaEntryResponse(
        LocalTime scheduledTime,
        AgendaEntryType type,
        String title,
        String details,
        AgendaEntryStatus status
    ) {
    }

    public record AgendaResponse(LocalDate date, String timeZone, List<AgendaEntryResponse> entries) {
    }
}
