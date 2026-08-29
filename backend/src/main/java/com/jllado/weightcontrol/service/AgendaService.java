package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.PushDtos.AgendaEntryResponse;
import com.jllado.weightcontrol.api.dto.PushDtos.AgendaEntryType;
import com.jllado.weightcontrol.api.dto.PushDtos.AgendaResponse;
import com.jllado.weightcontrol.domain.Medication;
import com.jllado.weightcontrol.domain.MedicationRepeatUnit;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MedicationRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AgendaService {

    private final RoutineRepository routineRepository;
    private final MedicationRepository medicationRepository;

    public AgendaService(RoutineRepository routineRepository, MedicationRepository medicationRepository) {
        this.routineRepository = routineRepository;
        this.medicationRepository = medicationRepository;
    }

    public AgendaResponse today(User user) {
        return agenda(user, ZonedDateTime.now(DateTimes.USER_ZONE).toLocalDate());
    }

    AgendaResponse agenda(User user, LocalDate date) {
        List<AgendaEntryResponse> entries = new java.util.ArrayList<>();
        for (MoodPeriod period : MoodPeriod.values()) {
            LocalTime time = reminderTime(user, period);
            String periodName = period.name().charAt(0) + period.name().substring(1).toLowerCase();
            entries.add(new AgendaEntryResponse(time, AgendaEntryType.MOOD, "Mood check-in", periodName));
            entries.add(new AgendaEntryResponse(time, AgendaEntryType.BACK_PAIN, "Back pain check-in", periodName));
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            entries.add(new AgendaEntryResponse(PushNotificationService.WEIGHT_REMINDER_TIME, AgendaEntryType.WEIGHT, "Weight reminder", null));
            entries.add(new AgendaEntryResponse(PushNotificationService.BLOOD_PRESSURE_REMINDER_TIME, AgendaEntryType.BLOOD_PRESSURE, "Blood pressure reminder", null));
        }
        routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(routine -> !DateTimes.toLocalDate(routine.getStartDate()).isAfter(date))
            .flatMap(routine -> routine.getReminders().stream().map(reminder -> new AgendaEntryResponse(
                reminder.getReminderTime(), AgendaEntryType.ROUTINE, routine.getName(), routine.getTypes().stream()
                    .map(type -> type.name().replace('_', ' '))
                    .collect(Collectors.joining(", "))
            )))
            .forEach(entries::add);
        medicationRepository.findByUserOrderByNameAsc(user).stream()
            .filter(medication -> isScheduledOn(medication, date))
            .flatMap(medication -> medication.getReminderTimes().stream().map(reminder -> new AgendaEntryResponse(
                reminder.getReminderTime(), AgendaEntryType.MEDICATION, medication.getName(), dose(medication)
            )))
            .forEach(entries::add);
        entries.sort(Comparator.comparing(AgendaEntryResponse::scheduledTime)
            .thenComparing(entry -> entry.type().name())
            .thenComparing(AgendaEntryResponse::title));
        return new AgendaResponse(date, DateTimes.USER_ZONE.getId(), entries);
    }

    private LocalTime reminderTime(User user, MoodPeriod period) {
        return switch (period) {
            case MORNING -> user.getMorningCheckInReminderTime();
            case MIDDAY -> user.getMiddayCheckInReminderTime();
            case EVENING -> user.getEveningCheckInReminderTime();
        };
    }

    private boolean isScheduledOn(Medication medication, LocalDate date) {
        if (!medication.isActive() || date.isBefore(medication.getStartDate()) || date.isAfter(medication.getEndDate())) {
            return false;
        }
        long intervalDays = medication.getRepeatUnit() == MedicationRepeatUnit.DAY
            ? medication.getRepeatEvery()
            : medication.getRepeatEvery() * 7L;
        return ChronoUnit.DAYS.between(medication.getStartDate(), date) % intervalDays == 0;
    }

    private String dose(Medication medication) {
        return medication.getDoseAmount().stripTrailingZeros().toPlainString() + " " + medication.getDoseUnit();
    }
}
