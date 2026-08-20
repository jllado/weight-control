package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.PushDtos.ReleaseNotificationRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.PushSubscriptionRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.ReminderSettingsRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.ReminderSettingsResponse;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.PushSubscription;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.repository.BloodPressureRepository;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.repository.PushSubscriptionRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeightRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PushNotificationService {

    static final int REMINDER_TTL_SECONDS = 11 * 60 * 60;
    static final int TEST_TTL_SECONDS = 60;
    static final int APP_UPDATE_TTL_SECONDS = 24 * 60 * 60;
    static final LocalTime WEIGHT_REMINDER_TIME = LocalTime.of(5, 0);
    static final LocalTime BLOOD_PRESSURE_REMINDER_TIME = LocalTime.of(5, 15);
    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCheckinRepository checkinRepository;
    private final MoodRepository moodRepository;
    private final BackPainEpisodeRepository backPainEpisodeRepository;
    private final WeightRepository weightRepository;
    private final BloodPressureRepository bloodPressureRepository;
    private final UserRepository userRepository;
    private final InAppNotificationService inAppNotificationService;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public PushNotificationService(
        PushSubscriptionRepository subscriptionRepository,
        RoutineRepository routineRepository,
        RoutineCheckinRepository checkinRepository,
        MoodRepository moodRepository,
        BackPainEpisodeRepository backPainEpisodeRepository,
        WeightRepository weightRepository,
        BloodPressureRepository bloodPressureRepository,
        UserRepository userRepository,
        InAppNotificationService inAppNotificationService,
        PushGateway gateway,
        ObjectMapper objectMapper,
        AppProperties properties
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.routineRepository = routineRepository;
        this.checkinRepository = checkinRepository;
        this.moodRepository = moodRepository;
        this.backPainEpisodeRepository = backPainEpisodeRepository;
        this.weightRepository = weightRepository;
        this.bloodPressureRepository = bloodPressureRepository;
        this.userRepository = userRepository;
        this.inAppNotificationService = inAppNotificationService;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void register(User user, PushSubscriptionRequest request) {
        requireEnabled();
        String endpointHash = endpointHash(request.endpoint());
        PushSubscription subscription = subscriptionRepository.findByEndpointHash(endpointHash).orElseGet(PushSubscription::new);
        subscription.setUser(user);
        subscription.setEndpoint(request.endpoint());
        subscription.setEndpointHash(endpointHash);
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        subscriptionRepository.save(subscription);
    }

    public void unregister(User user, String endpoint) {
        requireEnabled();
        subscriptionRepository.findByEndpointHash(endpointHash(endpoint))
            .filter(subscription -> subscription.getUser().getId().equals(user.getId()))
            .ifPresent(subscriptionRepository::delete);
    }

    public void sendTest(User user, String endpoint) {
        requireEnabled();
        PushSubscription subscription = requireOwned(user, endpoint);
        int status = gateway.send(subscription, testPayload(), TEST_TTL_SECONDS);
        if (isExpired(status)) {
            subscriptionRepository.delete(subscription);
            throw new BadRequestException("Push subscription is no longer valid");
        }
        if (status >= 300) {
            throw new PushDeliveryException("Push service returned HTTP " + status);
        }
    }

    public void sendAppUpdate(ReleaseNotificationRequest request) {
        requireEnabled();
        OffsetDateTime availableAt = ZonedDateTime.now(DateTimes.USER_ZONE).toOffsetDateTime();
        userRepository.findAll().forEach(user -> inAppNotificationService.recordAppUpdate(
            user,
            request.commitSha(),
            request.featureName(),
            availableAt
        ));
        String payload = appUpdatePayload(request.featureName());
        subscriptionRepository.findAll().forEach(subscription -> deliverScheduled(subscription, payload, APP_UPDATE_TTL_SECONDS));
    }

    public ReminderSettingsResponse reminderSettings(User user) {
        return reminderSettingsResponse(user);
    }

    public ReminderSettingsResponse updateReminderSettings(User user, ReminderSettingsRequest request) {
        LocalTime morning = request.morningTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime midday = request.middayTime().truncatedTo(ChronoUnit.MINUTES);
        LocalTime evening = request.eveningTime().truncatedTo(ChronoUnit.MINUTES);
        if (!morning.isBefore(midday) || !midday.isBefore(evening)) {
            throw new BadRequestException("Reminder times must be in morning, midday, and evening order");
        }
        user.setMorningCheckInReminderTime(morning);
        user.setMiddayCheckInReminderTime(midday);
        user.setEveningCheckInReminderTime(evening);
        return reminderSettingsResponse(userRepository.save(user));
    }

    @Scheduled(cron = "0 * * * * *", zone = "Europe/Madrid")
    public void sendRoutineReminders() {
        ZonedDateTime now = ZonedDateTime.now(DateTimes.USER_ZONE);
        sendRoutineReminders(now);
    }

    @Scheduled(cron = "0 * * * * *", zone = "Europe/Madrid")
    public void sendDailyCheckInReminders() {
        ZonedDateTime now = ZonedDateTime.now(DateTimes.USER_ZONE);
        sendDailyCheckInReminders(now.toLocalDate(), now.toLocalTime());
        sendWeeklyMeasurementReminders(now.toLocalDate(), now.toLocalTime());
    }

    void sendDailyCheckInReminders(LocalDate date, LocalTime time) {
        LocalTime reminderTime = time.truncatedTo(ChronoUnit.MINUTES);
        OffsetDateTime availableAt = ZonedDateTime.of(date, reminderTime, DateTimes.USER_ZONE).toOffsetDateTime();
        Map<Long, List<PushSubscription>> subscriptionsByUser = enabledSubscriptionsByUser();
        for (User user : userRepository.findAll()) {
            MoodPeriod period = reminderPeriod(user, reminderTime);
            if (period == null) {
                continue;
            }
            if (!moodRepository.existsByUserAndMoodDateAndPeriod(user, date, period)) {
                inAppNotificationService.recordMoodReminder(user, period, date, availableAt);
                String moodPayload = moodPayload(period, date);
                deliverReminder(subscriptionsByUser.get(user.getId()), moodPayload);
            }
            if (!backPainEpisodeRepository.existsByUserAndEpisodeDateAndPeriod(user, date, period)) {
                inAppNotificationService.recordBackReminder(user, period, date, availableAt);
                String backPayload = backPayload(period, date);
                deliverReminder(subscriptionsByUser.get(user.getId()), backPayload);
            }
        }
    }

    void sendWeeklyMeasurementReminders(LocalDate date, LocalTime time) {
        LocalTime reminderTime = time.truncatedTo(ChronoUnit.MINUTES);
        if (date.getDayOfWeek() != DayOfWeek.SATURDAY
            || (!reminderTime.equals(WEIGHT_REMINDER_TIME) && !reminderTime.equals(BLOOD_PRESSURE_REMINDER_TIME))) {
            return;
        }

        OffsetDateTime startOfDay = DateTimes.startOfDay(date);
        OffsetDateTime endOfDay = DateTimes.startOfDay(date.plusDays(1));
        OffsetDateTime availableAt = ZonedDateTime.of(date, reminderTime, DateTimes.USER_ZONE).toOffsetDateTime();
        Map<Long, List<PushSubscription>> subscriptionsByUser = enabledSubscriptionsByUser();
        for (User user : userRepository.findAll()) {
            if (reminderTime.equals(WEIGHT_REMINDER_TIME)
                && !weightRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(user, startOfDay, endOfDay)) {
                inAppNotificationService.recordWeightReminder(user, date, availableAt);
                deliverReminder(subscriptionsByUser.get(user.getId()), weightPayload(date));
            }
            if (reminderTime.equals(BLOOD_PRESSURE_REMINDER_TIME)
                && !bloodPressureRepository.existsByUserAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThan(user, startOfDay, endOfDay)) {
                inAppNotificationService.recordBloodPressureReminder(user, date, availableAt);
                deliverReminder(subscriptionsByUser.get(user.getId()), bloodPressurePayload(date));
            }
        }
    }

    void sendRoutineReminders(LocalDate date, LocalTime time) {
        sendRoutineReminders(ZonedDateTime.of(date, time, DateTimes.USER_ZONE));
    }

    private void sendRoutineReminders(ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        Map<Long, List<PushSubscription>> subscriptionsByUser = enabledSubscriptionsByUser();
        for (Routine routine : routineRepository.findByReminderTime(time)) {
            clearSnooze(routine);
            deliverRoutineReminder(routine, date, now.toOffsetDateTime(), subscriptionsByUser);
        }
        OffsetDateTime startOfDay = DateTimes.startOfDay(date);
        for (Routine routine : routineRepository.findByReminderSnoozedUntilBetween(startOfDay, now.toOffsetDateTime())) {
            clearSnooze(routine);
            deliverRoutineReminder(routine, date, now.toOffsetDateTime(), subscriptionsByUser);
        }
    }

    private void clearSnooze(Routine routine) {
        if (routine.getReminderSnoozedUntil() != null) {
            routine.setReminderSnoozedUntil(null);
            routineRepository.save(routine);
        }
    }

    private void deliverRoutineReminder(Routine routine, LocalDate date, OffsetDateTime availableAt, Map<Long, List<PushSubscription>> subscriptionsByUser) {
        if (DateTimes.toLocalDate(routine.getStartDate()).isAfter(date) || isCompleted(routine, date)) {
            return;
        }
        inAppNotificationService.recordRoutineReminder(routine, date, availableAt);
        String payload = routinePayload(routine, date);
        deliverReminder(subscriptionsByUser.get(routine.getUser().getId()), payload);
    }

    private boolean isCompleted(Routine routine, LocalDate date) {
        return checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine,
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date.plusDays(1))
        );
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

    private void deliverReminder(List<PushSubscription> subscriptions, String payload) {
        if (subscriptions != null) {
            subscriptions.forEach(subscription -> deliverScheduled(subscription, payload, REMINDER_TTL_SECONDS));
        }
    }

    private MoodPeriod reminderPeriod(User user, LocalTime time) {
        if (time.equals(user.getMorningCheckInReminderTime())) {
            return MoodPeriod.MORNING;
        }
        if (time.equals(user.getMiddayCheckInReminderTime())) {
            return MoodPeriod.MIDDAY;
        }
        if (time.equals(user.getEveningCheckInReminderTime())) {
            return MoodPeriod.EVENING;
        }
        return null;
    }

    private void deliverScheduled(PushSubscription subscription, String payload, int ttlSeconds) {
        try {
            int status = gateway.send(subscription, payload, ttlSeconds);
            if (isExpired(status)) {
                subscriptionRepository.delete(subscription);
            } else if (status >= 300) {
                LOG.warn("Push service returned HTTP {} for subscription {}", status, subscription.getId());
            }
        } catch (PushDeliveryException e) {
            LOG.error("Failed to deliver push notification for subscription {}", subscription.getId(), e);
        }
    }

    private String routinePayload(Routine routine, LocalDate date) {
        String url = "/?routineReminderId=" + routine.getId() + "&routineReminderDate=" + date;
        String snoozeUrl = "/api/routines/" + routine.getId() + "/reminder-snooze";
        return serialize(new PushPayload("Routine reminder", routine.getName(), url, "routine-reminder-" + routine.getId(), snoozeUrl));
    }

    private String moodPayload(MoodPeriod period, LocalDate date) {
        String label = periodLabel(period);
        String url = "/?checkInReminder=mood&checkInPeriod=" + period + "&checkInReminderDate=" + date;
        return serialize(new PushPayload(label + " mood reminder", "Record your " + label.toLowerCase() + " mood.", url, "mood-reminder-" + period, null));
    }

    private String backPayload(MoodPeriod period, LocalDate date) {
        String label = periodLabel(period);
        String url = "/?checkInReminder=back&checkInPeriod=" + period + "&checkInReminderDate=" + date;
        return serialize(new PushPayload(label + " back reminder", "Record a back pain episode if needed.", url, "back-reminder-" + period, null));
    }

    private String weightPayload(LocalDate date) {
        String url = "/?measurementReminder=weight&measurementReminderDate=" + date;
        return serialize(new PushPayload("Weight reminder", "Record your weight.", url, "weight-reminder", null));
    }

    private String bloodPressurePayload(LocalDate date) {
        String url = "/?measurementReminder=blood-pressure&measurementReminderDate=" + date;
        return serialize(new PushPayload("Blood pressure reminder", "Record your blood pressure.", url, "blood-pressure-reminder", null));
    }

    private String periodLabel(MoodPeriod period) {
        return switch (period) {
            case MORNING -> "Morning";
            case MIDDAY -> "Midday";
            case EVENING -> "Evening";
        };
    }

    private String testPayload() {
        return serialize(new PushPayload("Notification test", "Notifications are working.", "/", "routine-reminder-test", null));
    }

    private String appUpdatePayload(String featureName) {
        return serialize(new PushPayload(
            "Weight Control update available",
            featureName,
            "/",
            "weight-control-update",
            null
        ));
    }

    private ReminderSettingsResponse reminderSettingsResponse(User user) {
        return new ReminderSettingsResponse(
            user.getMorningCheckInReminderTime(),
            user.getMiddayCheckInReminderTime(),
            user.getEveningCheckInReminderTime(),
            DateTimes.USER_ZONE.getId()
        );
    }

    private String serialize(PushPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not create push notification payload", e);
        }
    }

    private PushSubscription requireOwned(User user, String endpoint) {
        return subscriptionRepository.findByEndpointHash(endpointHash(endpoint))
            .filter(subscription -> subscription.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new NotFoundException("Push subscription not found"));
    }

    private void requireEnabled() {
        if (!properties.push().enabled()) {
            throw new BadRequestException("Push notifications are disabled");
        }
    }

    private boolean isExpired(int status) {
        return status == 404 || status == 410;
    }

    private String endpointHash(String endpoint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record PushPayload(String title, String body, String url, String tag, String snoozeUrl) {
    }
}
