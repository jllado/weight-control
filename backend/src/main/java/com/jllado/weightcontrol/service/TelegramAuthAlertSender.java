package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.config.CoachAuthAlertProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TelegramAuthAlertSender {
    private static final Logger LOG = LoggerFactory.getLogger(TelegramAuthAlertSender.class);
    private static final Duration RETRY_DELAY = Duration.ofMinutes(15);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper;
    private final String chatId;
    private final URI endpoint;

    @Autowired
    public TelegramAuthAlertSender(CoachAuthAlertProperties properties, ObjectMapper mapper) {
        this(properties, mapper, URI.create("https://api.telegram.org/bot" + properties.botToken() + "/sendMessage"));
    }

    TelegramAuthAlertSender(CoachAuthAlertProperties properties, ObjectMapper mapper, URI endpoint) {
        this.mapper = mapper;
        this.chatId = properties.chatId();
        this.endpoint = endpoint;
    }

    public DeliveryResult send(String text) {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("chat_id", chatId, "text", text))))
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = mapper.readTree(response.body());
            if (response.statusCode() == 200 && body != null && body.path("ok").asBoolean()) {
                LOG.info("Telegram Coach authentication alert delivered");
                return new DeliveryResult(true, Duration.ZERO);
            }
            long retrySeconds = body == null ? 0 : body.path("parameters").path("retry_after").asLong();
            LOG.warn("Telegram authentication alert delivery failed: HTTP {}", response.statusCode());
            return new DeliveryResult(false, Duration.ofSeconds(Math.max(RETRY_DELAY.toSeconds(), retrySeconds)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Telegram authentication alert delivery interrupted");
        } catch (IOException | IllegalArgumentException e) {
            // Exception messages can contain the credential-bearing Telegram URL.
            LOG.warn("Telegram authentication alert delivery failed: {}", e.getClass().getSimpleName());
        }
        return new DeliveryResult(false, RETRY_DELAY);
    }

    @PreDestroy
    public void close() {
        client.close();
    }

    public record DeliveryResult(boolean delivered, Duration retryAfter) { }
}
