package com.jllado.weightcontrol.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    Auth auth,
    Cors cors,
    Storage storage,
    ChatGptActions chatGptActions,
    Push push,
    WeeklySummary weeklySummary
) {

    public record Auth(
        String googleClientId,
        String jwtSecret,
        int jwtExpirationDays,
        boolean secureCookie
    ) {
        public Duration jwtExpiration() {
            return Duration.ofDays(jwtExpirationDays);
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Storage(Path root) {
    }

    public record ChatGptActions(
        String token,
        String userEmail,
        String publicBaseUrl,
        String fileSigningSecret
    ) {
    }

    public record Push(
        boolean enabled,
        String publicKey,
        String privateKey,
        String subject,
        String releaseToken
    ) {
    }

    public record WeeklySummary(
        boolean enabled,
        String ownerEmail,
        String recipientEmail,
        String senderEmail,
        String appUrl
    ) {
    }
}
