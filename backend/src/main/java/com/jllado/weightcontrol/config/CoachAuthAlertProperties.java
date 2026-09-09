package com.jllado.weightcontrol.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.coach-auth-alerts")
public record CoachAuthAlertProperties(boolean enabled, String botToken, String chatId, String userAgentProduct) {
    public CoachAuthAlertProperties {
        if (enabled && (botToken.isBlank() || chatId.isBlank() || userAgentProduct.isBlank())) {
            throw new IllegalArgumentException("Enabled Coach authentication alerts require Telegram credentials and a verified User-Agent product");
        }
    }
}
