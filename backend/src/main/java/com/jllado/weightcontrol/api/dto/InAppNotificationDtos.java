package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.InAppNotification;
import com.jllado.weightcontrol.domain.InAppNotificationType;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class InAppNotificationDtos {

    private InAppNotificationDtos() {
    }

    public record PendingNotificationResponse(
        Long id,
        InAppNotificationType type,
        String title,
        String message,
        LocalDate reminderDate,
        OffsetDateTime availableAt,
        String actionUrl
    ) {
        public static PendingNotificationResponse from(InAppNotification notification) {
            return new PendingNotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReminderDate(),
                notification.getAvailableAt(),
                actionUrl(notification)
            );
        }

        private static String actionUrl(InAppNotification notification) {
            return switch (notification.getType()) {
                case ROUTINE -> "/?routineReminderId=" + notification.getRoutineReminder().getRoutine().getId()
                    + "&routineReminderScheduleId=" + notification.getRoutineReminder().getId()
                    + "&routineReminderDate=" + notification.getReminderDate()
                    + "&notificationId=" + notification.getId();
                case MOOD -> checkInActionUrl(notification, "mood");
                case BACK -> checkInActionUrl(notification, "back");
                case WEIGHT -> measurementActionUrl(notification, "weight");
                case BLOOD_PRESSURE -> measurementActionUrl(notification, "blood-pressure");
                case APP_UPDATE -> "/";
            };
        }

        private static String checkInActionUrl(InAppNotification notification, String type) {
            return "/?checkInReminder=" + type
                + "&checkInPeriod=" + notification.getPeriod()
                + "&checkInReminderDate=" + notification.getReminderDate()
                + "&notificationId=" + notification.getId();
        }

        private static String measurementActionUrl(InAppNotification notification, String type) {
            return "/?measurementReminder=" + type
                + "&measurementReminderDate=" + notification.getReminderDate()
                + "&notificationId=" + notification.getId();
        }
    }
}
