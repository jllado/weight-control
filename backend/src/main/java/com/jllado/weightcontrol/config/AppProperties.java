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
    ChatGptActions chatGptActions
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
        String userEmail
    ) {
    }
}
