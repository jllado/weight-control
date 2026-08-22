package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationRequest;
import com.jllado.weightcontrol.domain.Medication;
import com.jllado.weightcontrol.domain.MedicationDose;
import com.jllado.weightcontrol.domain.MedicationDoseSource;
import com.jllado.weightcontrol.domain.MedicationDoseStatus;
import com.jllado.weightcontrol.domain.MedicationReminderTime;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MedicationDoseRepository;
import com.jllado.weightcontrol.repository.MedicationRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class MedicationService {

    private static final Set<Integer> SNOOZE_MINUTES = Set.of(15, 30, 60);
    private static final Set<MedicationDoseStatus> ACTIONABLE_STATUSES = Set.of(MedicationDoseStatus.PENDING, MedicationDoseStatus.SNOOZED);

    private final MedicationRepository repository;
    private final MedicationDoseRepository doseRepository;
    private final InAppNotificationService inAppNotificationService;

    public MedicationService(
        MedicationRepository repository,
        MedicationDoseRepository doseRepository,
        InAppNotificationService inAppNotificationService
    ) {
        this.repository = repository;
        this.doseRepository = doseRepository;
        this.inAppNotificationService = inAppNotificationService;
    }

    public List<Medication> findAll(User user) {
        return repository.findByUserOrderByNameAsc(user);
    }

    public Medication create(User user, MedicationRequest request) {
        validate(request);
        Medication medication = new Medication();
        medication.setUser(user);
        apply(medication, request);
        return repository.save(medication);
    }

    public Medication update(User user, Long id, MedicationRequest request) {
        validate(request);
        Medication medication = requireOwned(user, id);
        apply(medication, request);
        return repository.save(medication);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public List<MedicationDose> findDoses(User user, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException("Medication dose range start must not be after the end");
        }
        return doseRepository.findByMedicationUserAndScheduledAtBetweenOrderByScheduledAtDesc(
            user,
            DateTimes.startOfDay(from),
            DateTimes.startOfDay(to.plusDays(1)).minusNanos(1)
        );
    }

    public MedicationDose findDose(User user, Long id) {
        return requireOwnedDose(user, id);
    }

    public MedicationDose takeDose(User user, Long id, OffsetDateTime takenAt) {
        MedicationDose dose = requireOwnedDose(user, id);
        if (!ACTIONABLE_STATUSES.contains(dose.getStatus())) {
            throw new BadRequestException("Medication dose is not pending");
        }
        markTaken(dose, takenAt);
        inAppNotificationService.completeMedicationDose(dose);
        return doseRepository.save(dose);
    }

    public MedicationDose logDose(User user, Long medicationId, OffsetDateTime takenAt) {
        Medication medication = requireOwned(user, medicationId);
        MedicationDose dose = doseRepository.findFirstByMedicationAndStatusInOrderByScheduledAtDesc(medication, ACTIONABLE_STATUSES)
            .orElseGet(() -> newDose(medication, takenAt, MedicationDoseSource.MANUAL));
        markTaken(dose, takenAt);
        if (dose.getId() != null) {
            inAppNotificationService.completeMedicationDose(dose);
        }
        return doseRepository.save(dose);
    }

    public OffsetDateTime snoozeDose(User user, Long id, int minutes) {
        return snoozeDose(user, id, minutes, ZonedDateTime.now(DateTimes.USER_ZONE));
    }

    OffsetDateTime snoozeDose(User user, Long id, int minutes, ZonedDateTime now) {
        if (!SNOOZE_MINUTES.contains(minutes)) {
            throw new BadRequestException("Medication reminder snooze must be 15, 30, or 60 minutes");
        }
        MedicationDose dose = requireOwnedDose(user, id);
        if (!ACTIONABLE_STATUSES.contains(dose.getStatus())) {
            throw new BadRequestException("Medication dose is not pending");
        }
        OffsetDateTime nextReminderAt = now.plusMinutes(minutes).toOffsetDateTime();
        dose.setStatus(MedicationDoseStatus.SNOOZED);
        dose.setSnoozedUntil(nextReminderAt);
        doseRepository.save(dose);
        inAppNotificationService.snoozeMedicationDose(dose, nextReminderAt);
        return nextReminderAt;
    }

    Medication requireOwned(User user, Long id) {
        Medication medication = repository.findById(id).orElseThrow(() -> new NotFoundException("Medication not found"));
        if (!medication.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Medication not found");
        }
        return medication;
    }

    private MedicationDose requireOwnedDose(User user, Long id) {
        MedicationDose dose = doseRepository.findById(id).orElseThrow(() -> new NotFoundException("Medication dose not found"));
        if (!dose.getMedication().getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Medication dose not found");
        }
        return dose;
    }

    private void validate(MedicationRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new BadRequestException("Medication start date must not be after the end date");
        }
        List<LocalTime> normalizedTimes = request.reminderTimes().stream()
            .map(time -> time.truncatedTo(ChronoUnit.MINUTES))
            .toList();
        if (new LinkedHashSet<>(normalizedTimes).size() != normalizedTimes.size()) {
            throw new BadRequestException("Medication reminder times must be unique");
        }
    }

    private void apply(Medication medication, MedicationRequest request) {
        medication.setName(request.name().trim());
        medication.setDoseAmount(request.doseAmount());
        medication.setDoseUnit(request.doseUnit().trim());
        medication.setNotes(request.notes());
        medication.setStartDate(request.startDate());
        medication.setEndDate(request.endDate());
        medication.setRepeatEvery(request.repeatEvery());
        medication.setRepeatUnit(request.repeatUnit());
        medication.setActive(request.active());
        applyReminderTimes(medication, request.reminderTimes());
    }

    private void applyReminderTimes(Medication medication, List<LocalTime> reminderTimes) {
        Set<LocalTime> normalizedTimes = reminderTimes.stream()
            .map(time -> time.truncatedTo(ChronoUnit.MINUTES))
            .sorted()
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        medication.getReminderTimes().removeIf(reminder -> !normalizedTimes.contains(reminder.getReminderTime()));
        Set<LocalTime> existingTimes = medication.getReminderTimes().stream()
            .map(MedicationReminderTime::getReminderTime)
            .collect(java.util.stream.Collectors.toSet());
        normalizedTimes.stream()
            .filter(time -> !existingTimes.contains(time))
            .forEach(time -> {
                MedicationReminderTime reminder = new MedicationReminderTime();
                reminder.setMedication(medication);
                reminder.setReminderTime(time);
                medication.getReminderTimes().add(reminder);
            });
    }

    static MedicationDose newDose(Medication medication, OffsetDateTime scheduledAt, MedicationDoseSource source) {
        MedicationDose dose = new MedicationDose();
        dose.setMedication(medication);
        dose.setScheduledAt(scheduledAt);
        dose.setStatus(MedicationDoseStatus.PENDING);
        dose.setSource(source);
        dose.setMedicationName(medication.getName());
        dose.setDoseAmount(medication.getDoseAmount());
        dose.setDoseUnit(medication.getDoseUnit());
        dose.setNotes(medication.getNotes());
        return dose;
    }

    private void markTaken(MedicationDose dose, OffsetDateTime takenAt) {
        dose.setStatus(MedicationDoseStatus.TAKEN);
        dose.setTakenAt(takenAt);
        dose.setSnoozedUntil(null);
    }
}
