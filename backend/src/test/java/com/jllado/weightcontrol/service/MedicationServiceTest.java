package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationRequest;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.MedicationDoseRepository;
import com.jllado.weightcontrol.repository.MedicationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicationServiceTest {

    @Mock
    private MedicationRepository repository;
    @Mock
    private MedicationDoseRepository doseRepository;
    @Mock
    private InAppNotificationService inAppNotificationService;
    @InjectMocks
    private MedicationService service;

    @Test
    void createStoresTheScheduleAndSeveralExactReminderTimes() {
        User user = user(1L);
        when(repository.save(any(Medication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Medication medication = service.create(user, request(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 12, 1),
            2,
            MedicationRepeatUnit.WEEK,
            List.of(LocalTime.of(20, 0, 45), LocalTime.of(8, 0, 30))
        ));

        assertEquals("Vitamin D", medication.getName());
        assertEquals(new BigDecimal("1.5"), medication.getDoseAmount());
        assertEquals(2, medication.getRepeatEvery());
        assertEquals(MedicationRepeatUnit.WEEK, medication.getRepeatUnit());
        assertEquals(List.of(LocalTime.of(8, 0), LocalTime.of(20, 0)), medication.getReminderTimes().stream().map(MedicationReminderTime::getReminderTime).toList());
    }

    @Test
    void createRejectsAnEndBeforeTheStartAndDuplicateReminderMinutes() {
        User user = user(1L);

        assertThrows(BadRequestException.class, () -> service.create(user, request(
            LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), 1, MedicationRepeatUnit.DAY, List.of(LocalTime.of(8, 0))
        )));
        assertThrows(BadRequestException.class, () -> service.create(user, request(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 1, MedicationRepeatUnit.DAY,
            List.of(LocalTime.of(8, 0, 10), LocalTime.of(8, 0, 50))
        )));

        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsAnotherUsersMedication() {
        User owner = user(1L);
        User anotherUser = user(2L);
        Medication medication = medication(10L, owner);
        when(repository.findById(10L)).thenReturn(Optional.of(medication));

        assertThrows(NotFoundException.class, () -> service.update(anotherUser, 10L, request(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), 1, MedicationRepeatUnit.DAY, List.of(LocalTime.of(8, 0))
        )));

        verify(repository, never()).save(any());
    }

    @Test
    void takeDoseRecordsTheExactTimeAndClearsItsNotification() {
        User user = user(1L);
        MedicationDose dose = dose(20L, medication(10L, user), MedicationDoseStatus.PENDING);
        OffsetDateTime takenAt = OffsetDateTime.parse("2026-08-22T08:03:14+02:00");
        when(doseRepository.findById(20L)).thenReturn(Optional.of(dose));
        when(doseRepository.save(dose)).thenReturn(dose);

        MedicationDose result = service.takeDose(user, 20L, takenAt);

        assertEquals(MedicationDoseStatus.TAKEN, result.getStatus());
        assertEquals(takenAt, result.getTakenAt());
        assertNull(result.getSnoozedUntil());
        verify(inAppNotificationService).completeMedicationDose(dose);
    }

    @Test
    void snoozeDoseStoresTheSelectedDelay() {
        User user = user(1L);
        MedicationDose dose = dose(20L, medication(10L, user), MedicationDoseStatus.PENDING);
        ZonedDateTime now = ZonedDateTime.parse("2026-08-22T08:00:00+02:00[Europe/Madrid]");
        when(doseRepository.findById(20L)).thenReturn(Optional.of(dose));

        OffsetDateTime result = service.snoozeDose(user, 20L, 30, now);

        assertEquals(OffsetDateTime.parse("2026-08-22T08:30:00+02:00"), result);
        assertEquals(MedicationDoseStatus.SNOOZED, dose.getStatus());
        verify(inAppNotificationService).snoozeMedicationDose(dose, result);
    }

    @Test
    void manualLogCompletesAnExistingPendingDoseBeforeCreatingAnother() {
        User user = user(1L);
        Medication medication = medication(10L, user);
        MedicationDose pending = dose(20L, medication, MedicationDoseStatus.PENDING);
        OffsetDateTime takenAt = OffsetDateTime.parse("2026-08-22T08:05:00+02:00");
        when(repository.findById(10L)).thenReturn(Optional.of(medication));
        when(doseRepository.findFirstByMedicationAndStatusInOrderByScheduledAtDesc(eq(medication), any())).thenReturn(Optional.of(pending));
        when(doseRepository.save(pending)).thenReturn(pending);

        MedicationDose result = service.logDose(user, 10L, takenAt);

        assertEquals(pending, result);
        assertEquals(MedicationDoseStatus.TAKEN, result.getStatus());
        assertEquals(takenAt, result.getTakenAt());
        verify(inAppNotificationService).completeMedicationDose(pending);
    }

    @Test
    void manualLogCreatesATakenDoseWithSnapshotDetails() {
        User user = user(1L);
        Medication medication = medication(10L, user);
        OffsetDateTime takenAt = OffsetDateTime.parse("2026-08-22T08:05:00+02:00");
        when(repository.findById(10L)).thenReturn(Optional.of(medication));
        when(doseRepository.findFirstByMedicationAndStatusInOrderByScheduledAtDesc(eq(medication), any())).thenReturn(Optional.empty());
        when(doseRepository.save(any(MedicationDose.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MedicationDose result = service.logDose(user, 10L, takenAt);

        assertEquals(MedicationDoseSource.MANUAL, result.getSource());
        assertEquals(MedicationDoseStatus.TAKEN, result.getStatus());
        assertEquals("Vitamin D", result.getMedicationName());
        assertEquals(takenAt, result.getScheduledAt());
        verifyNoInteractions(inAppNotificationService);
    }

    private MedicationRequest request(LocalDate start, LocalDate end, int repeatEvery, MedicationRepeatUnit repeatUnit, List<LocalTime> times) {
        return new MedicationRequest(" Vitamin D ", new BigDecimal("1.5"), " tablet ", "With food", start, end, repeatEvery, repeatUnit, times, true);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Medication medication(Long id, User user) {
        Medication medication = new Medication();
        medication.setId(id);
        medication.setUser(user);
        medication.setName("Vitamin D");
        medication.setDoseAmount(new BigDecimal("1.5"));
        medication.setDoseUnit("tablet");
        medication.setNotes("With food");
        return medication;
    }

    private MedicationDose dose(Long id, Medication medication, MedicationDoseStatus status) {
        MedicationDose dose = MedicationService.newDose(medication, OffsetDateTime.parse("2026-08-22T08:00:00+02:00"), MedicationDoseSource.SCHEDULED);
        dose.setId(id);
        dose.setStatus(status);
        return dose;
    }
}
