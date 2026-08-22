package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.MedicationDoseRepository;
import com.jllado.weightcontrol.repository.MedicationReminderTimeRepository;
import com.jllado.weightcontrol.repository.PushSubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicationReminderServiceTest {

    @Mock
    private MedicationReminderTimeRepository reminderRepository;
    @Mock
    private MedicationDoseRepository doseRepository;
    @Mock
    private PushSubscriptionRepository subscriptionRepository;
    @Mock
    private InAppNotificationService inAppNotificationService;
    @Mock
    private PushGateway gateway;
    private MedicationReminderService service;

    @BeforeEach
    void setUp() {
        service = new MedicationReminderService(
            reminderRepository,
            doseRepository,
            subscriptionRepository,
            inAppNotificationService,
            gateway,
            new ObjectMapper(),
            new AppProperties(null, null, null, null, new AppProperties.Push(true, "public", "private", "subject", "release"), null)
        );
        lenient().when(doseRepository.save(any(MedicationDose.class))).thenAnswer(invocation -> {
            MedicationDose dose = invocation.getArgument(0);
            if (dose.getId() == null) {
                dose.setId(50L);
            }
            return dose;
        });
    }

    @Test
    void sendsAnInclusiveEndDateReminderOnAnEveryTwoWeekSchedule() {
        ZonedDateTime now = ZonedDateTime.parse("2026-08-29T08:00:00+02:00[Europe/Madrid]");
        User user = user(1L);
        Medication medication = medication(user, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 29), 2, MedicationRepeatUnit.WEEK);
        MedicationReminderTime reminder = reminder(medication, LocalTime.of(8, 0));
        PushSubscription subscription = subscription(user);
        when(reminderRepository.findByReminderTime(LocalTime.of(8, 0))).thenReturn(List.of(reminder));
        when(doseRepository.findByMedicationAndScheduledAt(medication, now.toOffsetDateTime())).thenReturn(Optional.empty());
        when(subscriptionRepository.findAll()).thenReturn(List.of(subscription));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS))).thenReturn(201);

        service.sendMedicationReminders(now);

        ArgumentCaptor<MedicationDose> dose = ArgumentCaptor.forClass(MedicationDose.class);
        verify(doseRepository).save(dose.capture());
        assertEquals(now.toOffsetDateTime(), dose.getValue().getScheduledAt());
        assertEquals(MedicationDoseStatus.PENDING, dose.getValue().getStatus());
        verify(inAppNotificationService).recordMedicationReminder(dose.getValue(), now.toOffsetDateTime());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(eq(subscription), payload.capture(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
        assertTrue(payload.getValue().contains("\"url\":\"/?medicationDoseId=50\""));
        assertTrue(payload.getValue().contains("\"snoozeUrl\":\"/api/medications/doses/50/snooze\""));
    }

    @Test
    void skipsInactiveOffCadenceAndExpiredMedications() {
        ZonedDateTime now = ZonedDateTime.parse("2026-08-22T08:00:00+02:00[Europe/Madrid]");
        User user = user(1L);
        Medication offCadence = medication(user, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), 2, MedicationRepeatUnit.WEEK);
        Medication expired = medication(user, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 21), 1, MedicationRepeatUnit.DAY);
        Medication inactive = medication(user, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), 1, MedicationRepeatUnit.DAY);
        inactive.setActive(false);
        when(reminderRepository.findByReminderTime(LocalTime.of(8, 0))).thenReturn(List.of(
            reminder(offCadence, LocalTime.of(8, 0)),
            reminder(expired, LocalTime.of(8, 0)),
            reminder(inactive, LocalTime.of(8, 0))
        ));

        service.sendMedicationReminders(now);

        verify(doseRepository, never()).findByMedicationAndScheduledAt(any(), any());
        verify(doseRepository, never()).save(any());
        verifyNoInteractions(gateway, inAppNotificationService);
    }

    @Test
    void aNewScheduledDoseMarksEarlierActionableDosesMissed() {
        ZonedDateTime now = ZonedDateTime.parse("2026-08-22T20:00:00+02:00[Europe/Madrid]");
        User user = user(1L);
        Medication medication = medication(user, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), 1, MedicationRepeatUnit.DAY);
        MedicationDose earlier = MedicationService.newDose(medication, OffsetDateTime.parse("2026-08-22T08:00:00+02:00"), MedicationDoseSource.SCHEDULED);
        earlier.setId(40L);
        when(reminderRepository.findByReminderTime(LocalTime.of(20, 0))).thenReturn(List.of(reminder(medication, LocalTime.of(20, 0))));
        when(doseRepository.findByMedicationAndScheduledAt(medication, now.toOffsetDateTime())).thenReturn(Optional.empty());
        when(doseRepository.findByMedicationAndStatusInAndScheduledAtBefore(eq(medication), any(), eq(now.toOffsetDateTime()))).thenReturn(List.of(earlier));

        service.sendMedicationReminders(now);

        assertEquals(MedicationDoseStatus.MISSED, earlier.getStatus());
        verify(inAppNotificationService).completeMedicationDose(earlier);
        verify(inAppNotificationService).recordMedicationReminder(argThat(dose -> dose.getScheduledAt().equals(now.toOffsetDateTime())), eq(now.toOffsetDateTime()));
    }

    @Test
    void anExistingOccurrencePreventsDuplicateDelivery() {
        ZonedDateTime now = ZonedDateTime.parse("2026-08-22T08:00:00+02:00[Europe/Madrid]");
        User user = user(1L);
        Medication medication = medication(user, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), 1, MedicationRepeatUnit.DAY);
        MedicationDose existing = MedicationService.newDose(medication, now.toOffsetDateTime(), MedicationDoseSource.SCHEDULED);
        when(reminderRepository.findByReminderTime(LocalTime.of(8, 0))).thenReturn(List.of(reminder(medication, LocalTime.of(8, 0))));
        when(doseRepository.findByMedicationAndScheduledAt(medication, now.toOffsetDateTime())).thenReturn(Optional.of(existing));

        service.sendMedicationReminders(now);

        verify(doseRepository, never()).save(any());
        verifyNoInteractions(gateway, inAppNotificationService);
    }

    @Test
    void deliversASnoozedDoseAgainAtItsExactTime() {
        ZonedDateTime now = ZonedDateTime.parse("2026-08-22T08:30:00+02:00[Europe/Madrid]");
        User user = user(1L);
        Medication medication = medication(user, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), 1, MedicationRepeatUnit.DAY);
        MedicationDose dose = MedicationService.newDose(medication, OffsetDateTime.parse("2026-08-22T08:00:00+02:00"), MedicationDoseSource.SCHEDULED);
        dose.setId(60L);
        dose.setStatus(MedicationDoseStatus.SNOOZED);
        dose.setSnoozedUntil(now.toOffsetDateTime());
        PushSubscription subscription = subscription(user);
        when(doseRepository.findByStatusAndSnoozedUntilLessThanEqual(MedicationDoseStatus.SNOOZED, now.toOffsetDateTime())).thenReturn(List.of(dose));
        when(subscriptionRepository.findAll()).thenReturn(List.of(subscription));

        service.sendMedicationReminders(now);

        assertEquals(MedicationDoseStatus.PENDING, dose.getStatus());
        assertNull(dose.getSnoozedUntil());
        verify(inAppNotificationService).recordMedicationReminder(dose, now.toOffsetDateTime());
        verify(gateway).send(eq(subscription), anyString(), eq(PushNotificationService.REMINDER_TTL_SECONDS));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Medication medication(User user, LocalDate start, LocalDate end, int repeatEvery, MedicationRepeatUnit repeatUnit) {
        Medication medication = new Medication();
        medication.setId((long) System.identityHashCode(medication));
        medication.setUser(user);
        medication.setName("Vitamin D");
        medication.setDoseAmount(new BigDecimal("1"));
        medication.setDoseUnit("tablet");
        medication.setStartDate(start);
        medication.setEndDate(end);
        medication.setRepeatEvery(repeatEvery);
        medication.setRepeatUnit(repeatUnit);
        medication.setActive(true);
        return medication;
    }

    private MedicationReminderTime reminder(Medication medication, LocalTime time) {
        MedicationReminderTime reminder = new MedicationReminderTime();
        reminder.setMedication(medication);
        reminder.setReminderTime(time);
        return reminder;
    }

    private PushSubscription subscription(User user) {
        PushSubscription subscription = new PushSubscription();
        subscription.setId(70L);
        subscription.setUser(user);
        subscription.setEndpoint("https://push.example/phone");
        return subscription;
    }
}
