package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.InAppNotification;
import com.jllado.weightcontrol.domain.InAppNotificationType;
import com.jllado.weightcontrol.domain.MedicationDose;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.RoutineReminder;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.InAppNotificationRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class InAppNotificationService {

    private final InAppNotificationRepository repository;
    private final RoutineCheckinRepository routineCheckinRepository;
    private final MoodRepository moodRepository;
    private final BackPainEpisodeRepository backPainEpisodeRepository;
    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;

    public InAppNotificationService(
        InAppNotificationRepository repository,
        RoutineCheckinRepository routineCheckinRepository,
        MoodRepository moodRepository,
        BackPainEpisodeRepository backPainEpisodeRepository,
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository
    ) {
        this.repository = repository;
        this.routineCheckinRepository = routineCheckinRepository;
        this.moodRepository = moodRepository;
        this.backPainEpisodeRepository = backPainEpisodeRepository;
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
    }

    public List<InAppNotification> findPending(User user) {
        return findPending(user, ZonedDateTime.now(DateTimes.USER_ZONE));
    }

    List<InAppNotification> findPending(User user, ZonedDateTime now) {
        return repository.findPending(
                user,
                now.toLocalDate(),
                now.toOffsetDateTime(),
                InAppNotificationType.APP_UPDATE
            ).stream()
            .filter(this::isIncomplete)
            .toList();
    }

    public void recordRoutineReminder(RoutineReminder reminder, LocalDate date, OffsetDateTime availableAt) {
        String key = routineKey(reminder.getId(), date);
        InAppNotification notification = repository.findByUserAndDeduplicationKey(reminder.getRoutine().getUser(), key).orElseGet(InAppNotification::new);
        notification.setUser(reminder.getRoutine().getUser());
        notification.setType(InAppNotificationType.ROUTINE);
        notification.setRoutineReminder(reminder);
        notification.setReminderDate(date);
        notification.setPeriod(null);
        notification.setTitle("Routine reminder");
        notification.setMessage(reminder.getRoutine().getName());
        notification.setAvailableAt(availableAt);
        notification.setDeduplicationKey(key);
        repository.save(notification);
    }

    public void recordMedicationReminder(MedicationDose dose, OffsetDateTime availableAt) {
        String key = medicationKey(dose.getId());
        InAppNotification notification = repository.findByUserAndDeduplicationKey(dose.getMedication().getUser(), key).orElseGet(InAppNotification::new);
        notification.setUser(dose.getMedication().getUser());
        notification.setType(InAppNotificationType.MEDICATION);
        notification.setRoutineReminder(null);
        notification.setMedicationDose(dose);
        notification.setReminderDate(DateTimes.toLocalDate(dose.getScheduledAt()));
        notification.setPeriod(null);
        notification.setTitle("Medication reminder");
        notification.setMessage(dose.getMedicationName() + ": " + dose.getDoseAmount().stripTrailingZeros().toPlainString() + " " + dose.getDoseUnit());
        notification.setAvailableAt(availableAt);
        notification.setDeduplicationKey(key);
        repository.save(notification);
    }

    public void snoozeMedicationDose(MedicationDose dose, OffsetDateTime nextReminderAt) {
        repository.findByUserAndDeduplicationKey(dose.getMedication().getUser(), medicationKey(dose.getId()))
            .ifPresent(notification -> {
                notification.setAvailableAt(nextReminderAt);
                repository.save(notification);
            });
    }

    public void completeMedicationDose(MedicationDose dose) {
        repository.findByUserAndDeduplicationKey(dose.getMedication().getUser(), medicationKey(dose.getId()))
            .ifPresent(notification -> {
                notification.setDismissedAt(OffsetDateTime.now(DateTimes.USER_ZONE));
                repository.save(notification);
            });
    }

    public void recordMoodReminder(User user, MoodPeriod period, LocalDate date, OffsetDateTime availableAt) {
        String label = periodLabel(period);
        recordCheckInReminder(
            user,
            InAppNotificationType.MOOD,
            period,
            date,
            availableAt,
            label + " mood reminder",
            "Record your " + label.toLowerCase() + " mood."
        );
    }

    public void recordBackReminder(User user, MoodPeriod period, LocalDate date, OffsetDateTime availableAt) {
        String label = periodLabel(period);
        recordCheckInReminder(
            user,
            InAppNotificationType.BACK,
            period,
            date,
            availableAt,
            label + " back reminder",
            "Record a back pain episode if needed."
        );
    }

    public void recordWeightReminder(User user, LocalDate date, OffsetDateTime availableAt) {
        recordMeasurementReminder(
            user,
            InAppNotificationType.WEIGHT,
            date,
            availableAt,
            "Weight reminder",
            "Record your weight."
        );
    }

    public void recordBloodPressureReminder(User user, LocalDate date, OffsetDateTime availableAt) {
        recordMeasurementReminder(
            user,
            InAppNotificationType.BLOOD_PRESSURE,
            date,
            availableAt,
            "Blood pressure reminder",
            "Record your blood pressure."
        );
    }

    public void recordAppUpdate(User user, String commitSha, String featureName, OffsetDateTime availableAt) {
        String key = InAppNotificationType.APP_UPDATE + ":" + commitSha;
        if (repository.findByUserAndDeduplicationKey(user, key).isPresent()) {
            return;
        }
        InAppNotification notification = new InAppNotification();
        notification.setUser(user);
        notification.setType(InAppNotificationType.APP_UPDATE);
        notification.setRoutineReminder(null);
        notification.setReminderDate(availableAt.atZoneSameInstant(DateTimes.USER_ZONE).toLocalDate());
        notification.setPeriod(null);
        notification.setTitle("Weight Control update available");
        notification.setMessage(featureName);
        notification.setAvailableAt(availableAt);
        notification.setDeduplicationKey(key);
        repository.save(notification);
    }

    public void snoozeRoutineReminder(RoutineReminder reminder, LocalDate date, OffsetDateTime nextReminderAt) {
        repository.findByUserAndDeduplicationKey(
            reminder.getRoutine().getUser(),
            routineKey(reminder.getId(), date)
        ).ifPresent(notification -> {
            notification.setAvailableAt(nextReminderAt == null ? DateTimes.startOfDay(date.plusDays(1)) : nextReminderAt);
            repository.save(notification);
        });
    }

    public void dismiss(User user, Long id) {
        InAppNotification notification = repository.findByIdAndUser(id, user)
            .orElseThrow(() -> new NotFoundException("Notification not found"));
        notification.setDismissedAt(OffsetDateTime.now(DateTimes.USER_ZONE));
        repository.save(notification);
    }

    public void dismissAll(User user) {
        OffsetDateTime dismissedAt = OffsetDateTime.now(DateTimes.USER_ZONE);
        List<InAppNotification> notifications = findPending(user);
        notifications.forEach(notification -> notification.setDismissedAt(dismissedAt));
        repository.saveAll(notifications);
    }

    private void recordCheckInReminder(
        User user,
        InAppNotificationType type,
        MoodPeriod period,
        LocalDate date,
        OffsetDateTime availableAt,
        String title,
        String message
    ) {
        String key = type + ":" + period + ":" + date;
        InAppNotification notification = repository.findByUserAndDeduplicationKey(user, key).orElseGet(InAppNotification::new);
        notification.setUser(user);
        notification.setType(type);
        notification.setRoutineReminder(null);
        notification.setReminderDate(date);
        notification.setPeriod(period);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setAvailableAt(availableAt);
        notification.setDeduplicationKey(key);
        repository.save(notification);
    }

    private void recordMeasurementReminder(
        User user,
        InAppNotificationType type,
        LocalDate date,
        OffsetDateTime availableAt,
        String title,
        String message
    ) {
        String key = type + ":" + date;
        InAppNotification notification = repository.findByUserAndDeduplicationKey(user, key).orElseGet(InAppNotification::new);
        notification.setUser(user);
        notification.setType(type);
        notification.setRoutineReminder(null);
        notification.setReminderDate(date);
        notification.setPeriod(null);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setAvailableAt(availableAt);
        notification.setDeduplicationKey(key);
        repository.save(notification);
    }

    private boolean isIncomplete(InAppNotification notification) {
        return switch (notification.getType()) {
            case ROUTINE -> !routineCheckinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
                notification.getRoutineReminder().getRoutine(),
                DateTimes.startOfDay(notification.getReminderDate()),
                DateTimes.startOfDay(notification.getReminderDate().plusDays(1))
            );
            case MEDICATION -> true;
            case MOOD -> !moodRepository.existsByUserAndMoodDateAndPeriod(
                notification.getUser(),
                notification.getReminderDate(),
                notification.getPeriod()
            );
            case BACK -> !backPainEpisodeRepository.existsByUserAndEpisodeDateAndPeriod(
                notification.getUser(),
                notification.getReminderDate(),
                notification.getPeriod()
            );
            case WEIGHT -> !weightRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
                notification.getUser(),
                DateTimes.startOfDay(notification.getReminderDate()),
                DateTimes.startOfDay(notification.getReminderDate().plusDays(1))
            );
            case BLOOD_PRESSURE -> !bloodPressureRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(
                notification.getUser(),
                DateTimes.startOfDay(notification.getReminderDate()),
                DateTimes.startOfDay(notification.getReminderDate().plusDays(1))
            );
            case APP_UPDATE -> true;
        };
    }

    private String routineKey(Long reminderId, LocalDate date) {
        return InAppNotificationType.ROUTINE + ":" + reminderId + ":" + date;
    }

    private String medicationKey(Long doseId) {
        return InAppNotificationType.MEDICATION + ":" + doseId;
    }

    private String periodLabel(MoodPeriod period) {
        return switch (period) {
            case MORNING -> "Morning";
            case MIDDAY -> "Midday";
            case EVENING -> "Evening";
        };
    }
}
