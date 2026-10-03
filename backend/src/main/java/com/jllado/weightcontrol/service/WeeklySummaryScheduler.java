package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.weekly-summary.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class WeeklySummaryScheduler {

    private final UserRepository userRepository;
    private final WeeklySummaryService service;
    private final AppProperties properties;
    @Value("${app.weekly-summary.backfill.mode:}")
    private String backfillMode = "";

    public WeeklySummaryScheduler(UserRepository userRepository, WeeklySummaryService service, AppProperties properties) {
        this.userRepository = userRepository;
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "Europe/Madrid")
    public void sendScheduledSummary() {
        if (!properties.weeklySummary().enabled() || !backfillMode.isBlank()) {
            return;
        }
        User owner = userRepository.findByEmail(properties.weeklySummary().ownerEmail())
            .orElseThrow(() -> new IllegalStateException("Weekly summary owner not found"));
        service.send(owner);
    }
}
