package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.config.CoachAuthAlertProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TelegramAuthAlertSenderTest {
    private HttpServer server;
    private TelegramAuthAlertSender sender;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private int status = 200;
    private String responseBody = "{\"ok\":true}";
    private boolean slow;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bot-private-token/sendMessage", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (slow) {
                try { Thread.sleep(5500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        sender = new TelegramAuthAlertSender(new CoachAuthAlertProperties(true, "private-token", "chat", "ChatGPT-User"), mapper,
            URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/bot-private-token/sendMessage"));
    }

    @AfterEach
    void tearDown() {
        sender.close();
        server.stop(0);
    }

    @Test
    void sendsPlainTextToConfiguredChat() throws Exception {
        assertTrue(sender.send("Coach API authentication failed").delivered());
        var body = mapper.readTree(requestBody.get());
        assertEquals("chat", body.path("chat_id").asText());
        assertEquals("Coach API authentication failed", body.path("text").asText());
        assertFalse(body.has("parse_mode"));
        assertFalse(requestBody.get().contains("private-token"));
    }

    @Test
    void respectsTelegramRetryAfter() {
        status = 429;
        responseBody = "{\"ok\":false,\"parameters\":{\"retry_after\":1800}}";
        var result = sender.send("test");
        assertFalse(result.delivered());
        assertEquals(Duration.ofMinutes(30), result.retryAfter());
    }

    @Test
    void handlesUnsuccessfulApiResponse() {
        responseBody = "{\"ok\":false,\"description\":\"private-token\"}";
        var result = sender.send("test");
        assertFalse(result.delivered());
        assertEquals(Duration.ofMinutes(15), result.retryAfter());
    }

    @Test
    void handlesMalformedErrorWithoutLoggingTokenOrBody() {
        Logger logger = (Logger) LoggerFactory.getLogger(TelegramAuthAlertSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            status = 502;
            responseBody = "private-token invalid response";
            assertFalse(sender.send("test").delivered());
            assertFalse(appender.list.isEmpty());
            for (var event : appender.list) {
                assertFalse(event.getFormattedMessage().contains("private-token"));
                assertNull(event.getThrowableProxy());
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void timesOutSlowResponses() {
        slow = true;
        assertTimeout(Duration.ofSeconds(7), () -> assertFalse(sender.send("test").delivered()));
    }
}
