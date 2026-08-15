package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WeeklySummaryScheduler {

    private final UserRepository userRepository;
    private final WeeklySummaryService service;
    private final AppProperties properties;

    public WeeklySummaryScheduler(UserRepository userRepository, WeeklySummaryService service, AppProperties properties) {
        this.userRepository = userRepository;
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(cron = "0 0 8 * * SUN", zone = "Europe/Madrid")
    public void sendScheduledSummary() {
        if (!properties.weeklySummary().enabled()) {
            return;
        }
        User owner = userRepository.findByEmail(properties.weeklySummary().ownerEmail())
            .orElseThrow(() -> new IllegalStateException("Weekly summary owner not found"));
        service.send(owner);
    }
}
