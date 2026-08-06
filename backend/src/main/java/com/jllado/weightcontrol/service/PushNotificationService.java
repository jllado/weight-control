package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.PushDtos.PushSubscriptionRequest;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.PushSubscription;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.PushSubscriptionRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
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

    static final int DAILY_TTL_SECONDS = 11 * 60 * 60;
    static final int TEST_TTL_SECONDS = 60;
    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository repository;
    private final DailyStatusSnapshotService snapshotService;
    private final PushGateway gateway;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public PushNotificationService(
        PushSubscriptionRepository repository,
        DailyStatusSnapshotService snapshotService,
        PushGateway gateway,
        ObjectMapper objectMapper,
        AppProperties properties
    ) {
        this.repository = repository;
        this.snapshotService = snapshotService;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void register(User user, PushSubscriptionRequest request) {
        requireEnabled();
        String endpointHash = endpointHash(request.endpoint());
        PushSubscription subscription = repository.findByEndpointHash(endpointHash).orElseGet(PushSubscription::new);
        subscription.setUser(user);
        subscription.setEndpoint(request.endpoint());
        subscription.setEndpointHash(endpointHash);
        subscription.setP256dh(request.keys().p256dh());
        subscription.setAuth(request.keys().auth());
        repository.save(subscription);
    }

    public void unregister(User user, String endpoint) {
        requireEnabled();
        repository.findByEndpointHash(endpointHash(endpoint))
            .filter(subscription -> subscription.getUser().getId().equals(user.getId()))
            .ifPresent(repository::delete);
    }

    public void sendTest(User user, String endpoint) {
        requireEnabled();
        PushSubscription subscription = requireOwned(user, endpoint);
        int status = gateway.send(subscription, payload(user, LocalDate.now(DateTimes.USER_ZONE), true), TEST_TTL_SECONDS);
        if (isExpired(status)) {
            repository.delete(subscription);
            throw new BadRequestException("Push subscription is no longer valid");
        }
        if (status >= 300) {
            throw new PushDeliveryException("Push service returned HTTP " + status);
        }
    }

    @Scheduled(cron = "0 0 13 * * *", zone = "Europe/Madrid")
    public void sendDailyReminders() {
        if (!properties.push().enabled()) {
            return;
        }
        sendDailyReminders(LocalDate.now(DateTimes.USER_ZONE));
    }

    void sendDailyReminders(LocalDate date) {
        Map<Long, List<PushSubscription>> subscriptionsByUser = repository.findAll().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                subscription -> subscription.getUser().getId(),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));
        for (List<PushSubscription> subscriptions : subscriptionsByUser.values()) {
            User user = subscriptions.getFirst().getUser();
            String payload = payload(user, date, false);
            subscriptions.forEach(subscription -> deliverScheduled(subscription, payload));
        }
    }

    private void deliverScheduled(PushSubscription subscription, String payload) {
        try {
            int status = gateway.send(subscription, payload, DAILY_TTL_SECONDS);
            if (isExpired(status)) {
                repository.delete(subscription);
            } else if (status >= 300) {
                LOG.warn("Push service returned HTTP {} for subscription {}", status, subscription.getId());
            }
        } catch (PushDeliveryException e) {
            LOG.error("Failed to deliver push notification for subscription {}", subscription.getId(), e);
        }
    }

    private String payload(User user, LocalDate date, boolean test) {
        DailyStatus status = snapshotService.rebuild(user, date);
        int remaining = status.getTotalRoutines() - status.getRoutinesDone();
        String routineWord = remaining == 1 ? "routine" : "routines";
        PushPayload payload = new PushPayload(
            test ? "Routine reminder test" : "Routine reminder",
            remaining + " " + routineWord + " remaining today.",
            "/",
            test ? "routine-reminder-test" : "routine-reminder"
        );
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not create push notification payload", e);
        }
    }

    private PushSubscription requireOwned(User user, String endpoint) {
        return repository.findByEndpointHash(endpointHash(endpoint))
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
