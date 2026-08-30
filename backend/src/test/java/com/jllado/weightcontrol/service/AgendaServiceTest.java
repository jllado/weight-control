package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.PushDtos.AgendaEntryStatus;
import com.jllado.weightcontrol.domain.Medication;
import com.jllado.weightcontrol.domain.MedicationDose;
import com.jllado.weightcontrol.domain.MedicationDoseStatus;
import com.jllado.weightcontrol.domain.MedicationReminderTime;
import com.jllado.weightcontrol.domain.MedicationRepeatUnit;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineReminder;
import com.jllado.weightcontrol.domain.RoutineType;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    @Mock
    private RoutineRepository routineRepository;
    @Mock
    private MedicationRepository medicationRepository;
    @Mock
    private RoutineCheckinRepository routineCheckinRepository;
    @Mock
    private MedicationDoseRepository medicationDoseRepository;
    @Mock
    private MoodRepository moodRepository;
    @Mock
    private BackPainEpisodeRepository backPainEpisodeRepository;
    @Mock
    private WeightRepository weightRepository;
    @Mock
    private BloodPressureRepository bloodPressureRepository;
    private AgendaService service;

    @BeforeEach
    void setUp() {
        service = new AgendaService(
            routineRepository,
            medicationRepository,
            routineCheckinRepository,
            medicationDoseRepository,
            moodRepository,
            backPainEpisodeRepository,
            weightRepository,
            bloodPressureRepository
        );
    }

    @Test
    void agendaIncludesTodaySchedulesInChronologicalOrder() {
        User user = user();
        LocalDate saturday = LocalDate.of(2026, 8, 29);
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of(routine("Walk", LocalTime.of(8, 0), saturday)));
        when(medicationRepository.findByUserOrderByNameAsc(user)).thenReturn(List.of(medication("Vitamin D", LocalTime.of(7, 0), saturday, true, 1)));

        var agenda = service.agenda(user, saturday);

        assertEquals(saturday, agenda.date());
        assertEquals("Europe/Madrid", agenda.timeZone());
        assertEquals(List.of("Weight reminder", "Blood pressure reminder", "Vitamin D", "Back pain check-in", "Mood check-in", "Walk", "Back pain check-in", "Mood check-in", "Back pain check-in", "Mood check-in"),
            agenda.entries().stream().map(entry -> entry.title()).toList());
        assertEquals("1 tablet", agenda.entries().stream().filter(entry -> entry.title().equals("Vitamin D")).findFirst().orElseThrow().details());
    }

    @Test
    void agendaExcludesFutureRoutinesAndInactiveOrOffCycleMedications() {
        User user = user();
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of(routine("Future", LocalTime.of(8, 0), date.plusDays(1))));
        when(medicationRepository.findByUserOrderByNameAsc(user)).thenReturn(List.of(
            medication("Inactive", LocalTime.of(7, 0), date, false, 1),
            medication("Every other day", LocalTime.of(7, 0), date.minusDays(1), true, 2)
        ));

        var agenda = service.agenda(user, date);

        assertEquals(6, agenda.entries().size());
    }

    @Test
    void agendaIncludesOnlyTheEarliestReminderForEachRoutine() {
        User user = user();
        LocalDate date = LocalDate.of(2026, 8, 30);
        Routine breathing = routine("Breathing routine", LocalTime.of(7, 30), date);
        RoutineReminder eveningReminder = new RoutineReminder();
        eveningReminder.setRoutine(breathing);
        eveningReminder.setReminderTime(LocalTime.of(18, 0));
        breathing.getReminders().add(eveningReminder);
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of(breathing));

        var agenda = service.agenda(user, date);

        var entries = agenda.entries().stream().filter(entry -> entry.title().equals("Breathing routine")).toList();
        assertEquals(1, entries.size());
        assertEquals(LocalTime.of(7, 30), entries.getFirst().scheduledTime());
    }

    @Test
    void agendaDerivesCompletionStatusesFromRecordedData() {
        User user = user();
        LocalDate saturday = LocalDate.of(2026, 8, 29);
        Medication medication = medication("Vitamin D", LocalTime.of(7, 0), saturday, true, 1);
        MedicationDose missedDose = new MedicationDose();
        missedDose.setStatus(MedicationDoseStatus.MISSED);
        when(routineRepository.findByUserOrderByStartDateAsc(user)).thenReturn(List.of(routine("Walk", LocalTime.of(8, 0), saturday)));
        when(medicationRepository.findByUserOrderByNameAsc(user)).thenReturn(List.of(medication));
        when(moodRepository.existsByUserAndMoodDateAndPeriod(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(2) == MoodPeriod.MORNING);
        when(backPainEpisodeRepository.existsByUserAndEpisodeDateAndPeriod(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> invocation.getArgument(2) == MoodPeriod.MIDDAY);
        when(weightRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(user, DateTimes.startOfDay(saturday), DateTimes.startOfDay(saturday.plusDays(1)))).thenReturn(true);
        when(bloodPressureRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(user, DateTimes.startOfDay(saturday), DateTimes.startOfDay(saturday.plusDays(1)))).thenReturn(true);
        when(routineCheckinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(medicationDoseRepository.findByMedicationAndScheduledAt(medication, DateTimes.startOfDay(saturday).plusHours(7))).thenReturn(Optional.of(missedDose));

        var agenda = service.agenda(user, saturday);

        assertEquals(AgendaEntryStatus.COMPLETED, agenda.entries().stream().filter(entry -> entry.type().name().equals("MOOD") && entry.details().equals("Morning")).findFirst().orElseThrow().status());
        assertEquals(AgendaEntryStatus.RECORDED, agenda.entries().stream().filter(entry -> entry.type().name().equals("BACK_PAIN") && entry.details().equals("Midday")).findFirst().orElseThrow().status());
        assertEquals(AgendaEntryStatus.NO_ISSUE, agenda.entries().stream().filter(entry -> entry.type().name().equals("BACK_PAIN") && entry.details().equals("Morning")).findFirst().orElseThrow().status());
        assertEquals(AgendaEntryStatus.COMPLETED, agenda.entries().stream().filter(entry -> entry.title().equals("Weight reminder")).findFirst().orElseThrow().status());
        assertEquals(AgendaEntryStatus.COMPLETED, agenda.entries().stream().filter(entry -> entry.title().equals("Blood pressure reminder")).findFirst().orElseThrow().status());
        assertEquals(AgendaEntryStatus.COMPLETED, agenda.entries().stream().filter(entry -> entry.title().equals("Walk")).findFirst().orElseThrow().status());
        assertEquals(AgendaEntryStatus.MISSED, agenda.entries().stream().filter(entry -> entry.title().equals("Vitamin D")).findFirst().orElseThrow().status());
    }

    private User user() {
        User user = new User();
        user.setMorningCheckInReminderTime(LocalTime.of(7, 30));
        user.setMiddayCheckInReminderTime(LocalTime.of(13, 30));
        user.setEveningCheckInReminderTime(LocalTime.of(20, 30));
        return user;
    }

    private Routine routine(String name, LocalTime time, LocalDate startDate) {
        Routine routine = new Routine();
        routine.setName(name);
        routine.setStartDate(DateTimes.startOfDay(startDate));
        routine.setTypes(new LinkedHashSet<>(List.of(RoutineType.FLEXIBILITY)));
        RoutineReminder reminder = new RoutineReminder();
        reminder.setReminderTime(time);
        reminder.setRoutine(routine);
        routine.setReminders(new LinkedHashSet<>(List.of(reminder)));
        return routine;
    }

    private Medication medication(String name, LocalTime time, LocalDate startDate, boolean active, int repeatEvery) {
        Medication medication = new Medication();
        medication.setName(name);
        medication.setDoseAmount(BigDecimal.ONE);
        medication.setDoseUnit("tablet");
        medication.setStartDate(startDate);
        medication.setEndDate(startDate.plusDays(10));
        medication.setRepeatEvery(repeatEvery);
        medication.setRepeatUnit(MedicationRepeatUnit.DAY);
        medication.setActive(active);
        MedicationReminderTime reminder = new MedicationReminderTime();
        reminder.setMedication(medication);
        reminder.setReminderTime(time);
        medication.setReminderTimes(new LinkedHashSet<>(List.of(reminder)));
        return medication;
    }
}
