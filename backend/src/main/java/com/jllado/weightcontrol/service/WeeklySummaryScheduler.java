package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.ZonedDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "app.weekly-summary.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class WeeklySummaryScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(WeeklySummaryScheduler.class);

    private final UserRepository userRepository;
    private final WeeklySummaryService service;
    private final PushNotificationService pushNotificationService;
    private final AppProperties properties;
    @Value("${app.weekly-summary.backfill.mode:}")
    private String backfillMode = "";

    public WeeklySummaryScheduler(UserRepository userRepository, WeeklySummaryService service, PushNotificationService pushNotificationService, AppProperties properties) {
        this.userRepository = userRepository;
        this.service = service;
        this.pushNotificationService = pushNotificationService;
        this.properties = properties;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Europe/Madrid")
    public void sendScheduledSummary() {
        if (!backfillMode.isBlank()) return;
        User owner = userRepository.findByEmail(properties.weeklySummary().ownerEmail())
            .orElseThrow(() -> new IllegalStateException("Weekly summary owner not found"));
        ZonedDateTime now = ZonedDateTime.now(DateTimes.USER_ZONE);
        if (properties.weeklySummary().enabled()) {
            try {
                service.sendScheduled(owner, now);
            } catch (RuntimeException exception) {
                LOG.error("Scheduled weekly summary email failed for {}", owner.getEmail(), exception);
            }
        }
        service.remindScheduled(owner, now).ifPresent(pushNotificationService::sendWeeklySummaryReminder);
    }
}
