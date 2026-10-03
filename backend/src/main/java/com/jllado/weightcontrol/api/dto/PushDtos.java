package com.jllado.weightcontrol.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.DayOfWeek;
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
        @NotNull LocalTime eveningTime,
        @NotNull LocalTime weightTime,
        @NotNull LocalTime bloodPressureTime,
        @JsonDeserialize(using = ReminderDayDeserializer.class) DayOfWeek weightDay,
        @JsonDeserialize(using = ReminderDayDeserializer.class) DayOfWeek bloodPressureDay
    ) {
        public ReminderSettingsRequest(LocalTime morningTime, LocalTime middayTime, LocalTime eveningTime) {
            this(morningTime, middayTime, eveningTime, LocalTime.of(5, 0), LocalTime.of(5, 15), null, null);
        }
    }

    public static class ReminderDayDeserializer extends JsonDeserializer<DayOfWeek> {
        @Override
        public DayOfWeek deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return context.readValue(parser, DayOfWeek.class);
        }

        @Override
        public DayOfWeek getNullValue(DeserializationContext context) throws JsonMappingException {
            return context.reportInputMismatch(DayOfWeek.class, "Reminder day must not be null");
        }

        @Override
        public DayOfWeek getAbsentValue(DeserializationContext context) {
            return null;
        }
    }

    public record ReminderSettingsResponse(
        LocalTime morningTime,
        LocalTime middayTime,
        LocalTime eveningTime,
        LocalTime weightTime,
        LocalTime bloodPressureTime,
        DayOfWeek weightDay,
        DayOfWeek bloodPressureDay,
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
        AgendaEntryStatus status,
        Long routineId,
        Long routineReminderId,
        Long medicationId
    ) {
        public AgendaEntryResponse(LocalTime scheduledTime, AgendaEntryType type, String title, String details, AgendaEntryStatus status) {
            this(scheduledTime, type, title, details, status, null, null, null);
        }
    }

    public record AgendaResponse(LocalDate date, String timeZone, List<AgendaEntryResponse> entries) {
    }
}
