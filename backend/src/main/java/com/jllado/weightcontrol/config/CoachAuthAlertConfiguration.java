package com.jllado.weightcontrol.config;

import com.jllado.weightcontrol.service.CoachAuthAlertService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CoachAuthAlertProperties.class)
public class CoachAuthAlertConfiguration {
    @Bean(destroyMethod = "close")
    AlertWorker coachAuthAlertWorker(CoachAuthAlertProperties properties, CoachAuthAlertService alerts) {
        return new AlertWorker(properties.enabled(), alerts);
    }

    // Keep this executor private so Spring's existing reminder scheduler stays independent.
    static class AlertWorker implements AutoCloseable {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("coach-auth-alert").factory());

        AlertWorker(boolean enabled, CoachAuthAlertService alerts) {
            if (enabled) {
                executor.scheduleWithFixedDelay(alerts::deliverPending, 0, 1, TimeUnit.SECONDS);
            }
        }

        @Override
        public void close() {
            executor.close();
        }
    }
}
