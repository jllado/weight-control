package com.jllado.weightcontrol.util;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class DateTimes {

    public static final ZoneId USER_ZONE = ZoneId.of("Europe/Madrid");
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DateTimes() {
    }

    public static String formatDate(LocalDate date) {
        return DATE_FORMAT.format(date);
    }

    public static String formatDate(OffsetDateTime dateTime) {
        return DATE_FORMAT.format(dateTime.atZoneSameInstant(USER_ZONE));
    }

    public static String formatDateTime(OffsetDateTime dateTime) {
        return DATE_TIME_FORMAT.format(dateTime.atZoneSameInstant(USER_ZONE));
    }

    public static LocalDate toLocalDate(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(USER_ZONE).toLocalDate();
    }

    public static OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(USER_ZONE).toOffsetDateTime();
    }

    public static LocalDate startOfDashboardWeek(LocalDate date) {
        int daysSinceSaturday = (date.getDayOfWeek().getValue() + 1) % 7;
        return date.minusDays(daysSinceSaturday);
    }
}
