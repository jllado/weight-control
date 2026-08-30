package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.PushDtos.AgendaEntryResponse;
import com.jllado.weightcontrol.api.dto.PushDtos.AgendaEntryStatus;
import com.jllado.weightcontrol.api.dto.PushDtos.AgendaEntryType;
import com.jllado.weightcontrol.api.dto.PushDtos.AgendaResponse;
import com.jllado.weightcontrol.domain.Medication;
import com.jllado.weightcontrol.domain.MedicationDoseStatus;
import com.jllado.weightcontrol.domain.MedicationRepeatUnit;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MedicationRepository;
import com.jllado.weightcontrol.repository.MedicationDoseRepository;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
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
    private final RoutineCheckinRepository routineCheckinRepository;
    private final MedicationDoseRepository medicationDoseRepository;
    private final MoodRepository moodRepository;
    private final BackPainEpisodeRepository backPainEpisodeRepository;
    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;

    public AgendaService(
        RoutineRepository routineRepository,
        MedicationRepository medicationRepository,
        RoutineCheckinRepository routineCheckinRepository,
        MedicationDoseRepository medicationDoseRepository,
        MoodRepository moodRepository,
        BackPainEpisodeRepository backPainEpisodeRepository,
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository
    ) {
        this.routineRepository = routineRepository;
        this.medicationRepository = medicationRepository;
        this.routineCheckinRepository = routineCheckinRepository;
        this.medicationDoseRepository = medicationDoseRepository;
        this.moodRepository = moodRepository;
        this.backPainEpisodeRepository = backPainEpisodeRepository;
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
    }

    public AgendaResponse today(User user) {
        return agenda(user, ZonedDateTime.now(DateTimes.USER_ZONE).toLocalDate());
    }

    AgendaResponse agenda(User user, LocalDate date) {
        List<AgendaEntryResponse> entries = new java.util.ArrayList<>();
        for (MoodPeriod period : MoodPeriod.values()) {
            LocalTime time = reminderTime(user, period);
            String periodName = period.name().charAt(0) + period.name().substring(1).toLowerCase();
            entries.add(new AgendaEntryResponse(time, AgendaEntryType.MOOD, "Mood check-in", periodName,
                moodRepository.existsByUserAndMoodDateAndPeriod(user, date, period) ? AgendaEntryStatus.COMPLETED : AgendaEntryStatus.PENDING, null, null, null));
            entries.add(new AgendaEntryResponse(time, AgendaEntryType.BACK_PAIN, "Back pain check-in", periodName,
                backPainEpisodeRepository.existsByUserAndEpisodeDateAndPeriod(user, date, period) ? AgendaEntryStatus.RECORDED : AgendaEntryStatus.NO_ISSUE, null, null, null));
        }
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
            entries.add(new AgendaEntryResponse(user.getWeightReminderTime(), AgendaEntryType.WEIGHT, "Weight reminder", null,
                hasWeight(user, date) ? AgendaEntryStatus.COMPLETED : AgendaEntryStatus.PENDING, null, null, null));
            entries.add(new AgendaEntryResponse(user.getBloodPressureReminderTime(), AgendaEntryType.BLOOD_PRESSURE, "Blood pressure reminder", null,
                hasBloodPressure(user, date) ? AgendaEntryStatus.COMPLETED : AgendaEntryStatus.PENDING, null, null, null));
        }
        routineRepository.findByUserOrderByStartDateAsc(user).stream()
            .filter(routine -> !DateTimes.toLocalDate(routine.getStartDate()).isAfter(date))
            .flatMap(routine -> routine.getReminders().stream().findFirst().stream().map(reminder -> new AgendaEntryResponse(
                reminder.getReminderTime(), AgendaEntryType.ROUTINE, routine.getName(), routine.getTypes().stream()
                    .map(type -> type.name().replace('_', ' '))
                    .collect(Collectors.joining(", ")),
                routineCheckinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
                    routine, DateTimes.startOfDay(date), DateTimes.startOfDay(date.plusDays(1))
                ) ? AgendaEntryStatus.COMPLETED : AgendaEntryStatus.PENDING, routine.getId(), reminder.getId(), null
            )))
            .forEach(entries::add);
        medicationRepository.findByUserOrderByNameAsc(user).stream()
            .filter(medication -> isScheduledOn(medication, date))
            .flatMap(medication -> medication.getReminderTimes().stream().map(reminder -> new AgendaEntryResponse(
                reminder.getReminderTime(), AgendaEntryType.MEDICATION, medication.getName(), dose(medication),
                medicationStatus(medication, date, reminder.getReminderTime()), null, null, medication.getId()
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

    private boolean hasWeight(User user, LocalDate date) {
        return weightRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            user, DateTimes.startOfDay(date), DateTimes.startOfDay(date.plusDays(1))
        );
    }

    private boolean hasBloodPressure(User user, LocalDate date) {
        return bloodPressureRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
            user, DateTimes.startOfDay(date), DateTimes.startOfDay(date.plusDays(1))
        );
    }

    private AgendaEntryStatus medicationStatus(Medication medication, LocalDate date, LocalTime time) {
        return medicationDoseRepository.findByMedicationAndScheduledAt(medication, ZonedDateTime.of(date, time, DateTimes.USER_ZONE).toOffsetDateTime())
            .map(dose -> switch (dose.getStatus()) {
                case TAKEN -> AgendaEntryStatus.COMPLETED;
                case MISSED -> AgendaEntryStatus.MISSED;
                case PENDING, SNOOZED -> AgendaEntryStatus.PENDING;
            })
            .orElse(AgendaEntryStatus.PENDING);
    }
}
