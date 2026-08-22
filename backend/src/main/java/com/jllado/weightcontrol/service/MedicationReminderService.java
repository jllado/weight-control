package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.Medication;
import com.jllado.weightcontrol.domain.MedicationDose;
import com.jllado.weightcontrol.domain.MedicationDoseSource;
import com.jllado.weightcontrol.domain.MedicationDoseStatus;
import com.jllado.weightcontrol.domain.MedicationReminderTime;
import com.jllado.weightcontrol.domain.MedicationRepeatUnit;
import com.jllado.weightcontrol.domain.PushSubscription;
import com.jllado.weightcontrol.repository.MedicationDoseRepository;
import com.jllado.weightcontrol.repository.MedicationReminderTimeRepository;
import com.jllado.weightcontrol.repository.PushSubscriptionRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MedicationReminderService {

    private static final Logger LOG = LoggerFactory.getLogger(MedicationReminderService.class);
    private static final Set<MedicationDoseStatus> ACTIONABLE_STATUSES = Set.of(MedicationDoseStatus.PENDING, MedicationDoseStatus.SNOOZED);

    private final MedicationReminderTimeRepository reminderRepository;
    private final MedicationDoseRepository doseRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final InAppNotificationService inAppNotificationService;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public MedicationReminderService(
        MedicationReminderTimeRepository reminderRepository,
        MedicationDoseRepository doseRepository,
        PushSubscriptionRepository subscriptionRepository,
        InAppNotificationService inAppNotificationService,
        PushGateway gateway,
        ObjectMapper objectMapper,
        AppProperties properties
    ) {
        this.reminderRepository = reminderRepository;
        this.doseRepository = doseRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.inAppNotificationService = inAppNotificationService;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Europe/Madrid")
    public void sendMedicationReminders() {
        sendMedicationReminders(ZonedDateTime.now(DateTimes.USER_ZONE));
    }

    void sendMedicationReminders(ZonedDateTime now) {
        LocalTime time = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        OffsetDateTime scheduledAt = ZonedDateTime.of(now.toLocalDate(), time, DateTimes.USER_ZONE).toOffsetDateTime();
        Map<Long, List<PushSubscription>> subscriptionsByUser = enabledSubscriptionsByUser();

        for (MedicationReminderTime reminder : reminderRepository.findByReminderTime(time)) {
            Medication medication = reminder.getMedication();
            if (!isScheduledOn(medication, now.toLocalDate()) || doseRepository.findByMedicationAndScheduledAt(medication, scheduledAt).isPresent()) {
                continue;
            }
            markPreviousDosesMissed(medication, scheduledAt);
            MedicationDose dose = doseRepository.save(MedicationService.newDose(medication, scheduledAt, MedicationDoseSource.SCHEDULED));
            deliver(dose, scheduledAt, subscriptionsByUser);
        }

        for (MedicationDose dose : doseRepository.findByStatusAndSnoozedUntilLessThanEqual(MedicationDoseStatus.SNOOZED, now.toOffsetDateTime())) {
            dose.setStatus(MedicationDoseStatus.PENDING);
            dose.setSnoozedUntil(null);
            doseRepository.save(dose);
            deliver(dose, now.toOffsetDateTime(), subscriptionsByUser);
        }
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

    private void markPreviousDosesMissed(Medication medication, OffsetDateTime scheduledAt) {
        for (MedicationDose dose : doseRepository.findByMedicationAndStatusInAndScheduledAtBefore(medication, ACTIONABLE_STATUSES, scheduledAt)) {
            dose.setStatus(MedicationDoseStatus.MISSED);
            dose.setSnoozedUntil(null);
            doseRepository.save(dose);
            inAppNotificationService.completeMedicationDose(dose);
        }
    }

    private void deliver(MedicationDose dose, OffsetDateTime availableAt, Map<Long, List<PushSubscription>> subscriptionsByUser) {
        inAppNotificationService.recordMedicationReminder(dose, availableAt);
        List<PushSubscription> subscriptions = subscriptionsByUser.get(dose.getMedication().getUser().getId());
        if (subscriptions != null) {
            subscriptions.forEach(subscription -> deliver(subscription, payload(dose)));
        }
    }

    private Map<Long, List<PushSubscription>> enabledSubscriptionsByUser() {
        if (!properties.push().enabled()) {
            return Map.of();
        }
        return subscriptionRepository.findAll().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                subscription -> subscription.getUser().getId(),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));
    }

    private void deliver(PushSubscription subscription, String payload) {
        try {
            int status = gateway.send(subscription, payload, PushNotificationService.REMINDER_TTL_SECONDS);
            if (status == 404 || status == 410) {
                subscriptionRepository.delete(subscription);
            } else if (status >= 300) {
                LOG.warn("Push service returned HTTP {} for subscription {}", status, subscription.getId());
            }
        } catch (PushDeliveryException e) {
            LOG.error("Failed to deliver medication reminder for subscription {}", subscription.getId(), e);
        }
    }

    private String payload(MedicationDose dose) {
        return serialize(new MedicationPushPayload(
            "Medication reminder",
            dose.getMedicationName() + ": " + dose.getDoseAmount().stripTrailingZeros().toPlainString() + " " + dose.getDoseUnit(),
            "/?medicationDoseId=" + dose.getId(),
            "medication-dose-" + dose.getId(),
            "/api/medications/doses/" + dose.getId() + "/snooze"
        ));
    }

    private String serialize(MedicationPushPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not create medication notification payload", e);
        }
    }

    private record MedicationPushPayload(String title, String body, String url, String tag, String snoozeUrl) {
    }
}
