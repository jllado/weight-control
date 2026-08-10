package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.PushDtos.PushSubscriptionRequest;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.PushSubscription;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.PushSubscriptionRepository;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
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
    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCheckinRepository checkinRepository;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public PushNotificationService(
        PushSubscriptionRepository subscriptionRepository,
        RoutineRepository routineRepository,
        RoutineCheckinRepository checkinRepository,
        PushGateway gateway,
        ObjectMapper objectMapper,
        AppProperties properties
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.routineRepository = routineRepository;
        this.checkinRepository = checkinRepository;
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

    @Scheduled(cron = "0 * * * * *", zone = "Europe/Madrid")
    public void sendRoutineReminders() {
        if (!properties.push().enabled()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(DateTimes.USER_ZONE);
        sendRoutineReminders(now.toLocalDate(), now.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
    }

    void sendRoutineReminders(LocalDate date, LocalTime time) {
        Map<Long, List<PushSubscription>> subscriptionsByUser = subscriptionRepository.findAll().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                subscription -> subscription.getUser().getId(),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));
        for (Routine routine : routineRepository.findByReminderTime(time)) {
            List<PushSubscription> subscriptions = subscriptionsByUser.get(routine.getUser().getId());
            if (subscriptions == null || DateTimes.toLocalDate(routine.getStartDate()).isAfter(date) || isCompleted(routine, date)) {
                continue;
            }
            String payload = routinePayload(routine);
            subscriptions.forEach(subscription -> deliverScheduled(subscription, payload));
        }
    }

    private boolean isCompleted(Routine routine, LocalDate date) {
        return checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine,
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date.plusDays(1))
        );
    }

    private void deliverScheduled(PushSubscription subscription, String payload) {
        try {
            int status = gateway.send(subscription, payload, REMINDER_TTL_SECONDS);
            if (isExpired(status)) {
                subscriptionRepository.delete(subscription);
            } else if (status >= 300) {
                LOG.warn("Push service returned HTTP {} for subscription {}", status, subscription.getId());
            }
        } catch (PushDeliveryException e) {
            LOG.error("Failed to deliver push notification for subscription {}", subscription.getId(), e);
        }
    }

    private String routinePayload(Routine routine) {
        return serialize(new PushPayload("Routine reminder", routine.getName(), "/", "routine-reminder-" + routine.getId()));
    }

    private String testPayload() {
        return serialize(new PushPayload("Notification test", "Notifications are working.", "/", "routine-reminder-test"));
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

    private record PushPayload(String title, String body, String url, String tag) {
    }
}
