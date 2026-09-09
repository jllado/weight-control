package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jllado.weightcontrol.config.CoachAuthAlertProperties;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class CoachAuthAlertServiceTest {
    private final TelegramAuthAlertSender sender = mock(TelegramAuthAlertSender.class);
    private final MutableClock clock = new MutableClock();
    private final CoachAuthAlertService service = new CoachAuthAlertService(properties(true), sender, clock);

    private static CoachAuthAlertProperties properties(boolean enabled) {
        return new CoachAuthAlertProperties(enabled, "secret", "chat", "ChatGPT-User");
    }

    private void failure(String ua) {
        service.record("GET", "/api/chatgpt-actions/coach/workouts/2026-09-09/assessment-context", ua, CoachAuthAlertService.Reason.INVALID_TOKEN);
    }

    @Test
    void sendsFirstThenAggregatesAcrossEndpointsWithoutLeakingRequestValues() {
        when(sender.send(anyString())).thenReturn(new TelegramAuthAlertSender.DeliveryResult(true, Duration.ZERO));
        failure("Mozilla/5.0; compatible; ChatGPT-User/1.0; +https://openai.com/bot");
        verifyNoInteractions(sender);
        service.deliverPending();
        failure("ChatGPT-User/2.3");
        service.record("POST", "/api/chatgpt-actions/coach/sleeps", "ChatGPT-User/2.0", CoachAuthAlertService.Reason.MISSING_HEADER);
        service.deliverPending();
        verify(sender, times(1)).send(anyString());
        clock.advance(Duration.ofMinutes(15));
        service.deliverPending();
        service.deliverPending();
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).send(messages.capture());
        assertTrue(messages.getAllValues().get(0).contains("Failures: 1"));
        assertTrue(messages.getValue().contains("Failures: 2"));
        assertTrue(messages.getValue().contains("MISSING_HEADER"));
        assertTrue(messages.getValue().contains("/coach/workouts/{workoutDate}/assessment-context"));
        assertFalse(messages.getValue().contains("2026-09-09"));
        assertFalse(messages.getValue().contains("ChatGPT-User"));
        clock.advance(Duration.ofMinutes(30));
        failure("ChatGPT-User/1.0");
        service.deliverPending();
        verify(sender, times(3)).send(anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Mozilla/5.0", "FakeChatGPT-User/1.0", "ChatGPT-User-Fake/1.0", "ChatGPT-User", "ChatGPT-User/", "chatgpt-user/1.0"})
    void ignoresUnmatchedUserAgentsWithoutStartingCooldown(String ua) {
        failure(ua);
        service.deliverPending();
        verifyNoInteractions(sender);
        when(sender.send(anyString())).thenReturn(new TelegramAuthAlertSender.DeliveryResult(true, Duration.ZERO));
        failure("ChatGPT-User/1.0");
        service.deliverPending();
        verify(sender).send(anyString());
    }

    @Test
    void logsUnmatchedFailuresWithOnlySafeMetadata() {
        Logger logger = (Logger) LoggerFactory.getLogger(CoachAuthAlertService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.record("PRIVATE\nDATA", "/api/chatgpt-actions/private-secret", "token-secret", CoachAuthAlertService.Reason.INVALID_TOKEN);
            String message = appender.list.getFirst().getFormattedMessage();
            assertTrue(message.contains("method=OTHER endpoint=OTHER reason=INVALID_TOKEN"));
            assertFalse(message.contains("secret"));
            assertFalse(message.contains("PRIVATE"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void disabledAlertsStillDoNotContactTelegram() {
        CoachAuthAlertService disabled = new CoachAuthAlertService(properties(false), sender, clock);
        disabled.record("GET", "/api/chatgpt-actions/coach/catalog", "ChatGPT-User/1.0", CoachAuthAlertService.Reason.INVALID_TOKEN);
        disabled.deliverPending();
        verifyNoInteractions(sender);
    }

    @Test
    void retainsCountsAndRespectsLongerRetryDelay() {
        when(sender.send(anyString())).thenReturn(
            new TelegramAuthAlertSender.DeliveryResult(false, Duration.ofMinutes(30)),
            new TelegramAuthAlertSender.DeliveryResult(true, Duration.ZERO));
        failure("ChatGPT-User/1.0");
        service.deliverPending();
        failure("ChatGPT-User/1.0");
        clock.advance(Duration.ofMinutes(15));
        service.deliverPending();
        verify(sender).send(anyString());
        clock.advance(Duration.ofMinutes(15));
        service.deliverPending();
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).send(message.capture());
        assertTrue(message.getValue().contains("Failures: 2"));
    }

    @Test
    void concurrentRequestsDoNotWaitForTelegramAndAreRetained() throws Exception {
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(sender.send(anyString())).thenAnswer(invocation -> {
            sending.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new TelegramAuthAlertSender.DeliveryResult(true, Duration.ZERO);
        });
        failure("ChatGPT-User/1.0");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> delivery = executor.submit(service::deliverPending);
            assertTrue(sending.await(2, TimeUnit.SECONDS));
            try {
                var futures = java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> executor.submit(() -> failure("ChatGPT-User/1.0"))).toList();
                for (Future<?> future : futures) future.get(2, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            delivery.get(2, TimeUnit.SECONDS);
        }
        clock.advance(Duration.ofMinutes(15));
        service.deliverPending();
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).send(message.capture());
        assertTrue(message.getValue().contains("Failures: 100"));
    }

    @Test
    void arbitraryPathsUseOneSafeGroup() {
        when(sender.send(anyString())).thenReturn(new TelegramAuthAlertSender.DeliveryResult(true, Duration.ZERO));
        for (int i = 0; i < 100; i++) {
            service.record("GET", "/api/chatgpt-actions/secret-" + i, "ChatGPT-User/1.0", CoachAuthAlertService.Reason.INVALID_TOKEN);
        }
        service.deliverPending();
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender).send(message.capture());
        assertTrue(message.getValue().contains("GET OTHER — INVALID_TOKEN: 100"));
        assertFalse(message.getValue().contains("secret-"));
    }

    @Test
    void enabledConfigurationRequiresAllSettings() {
        assertThrows(IllegalArgumentException.class, () -> new CoachAuthAlertProperties(true, "", "chat", "ChatGPT-User"));
        assertThrows(IllegalArgumentException.class, () -> new CoachAuthAlertProperties(true, "token", "", "ChatGPT-User"));
        assertThrows(IllegalArgumentException.class, () -> new CoachAuthAlertProperties(true, "token", "chat", ""));
        assertDoesNotThrow(() -> new CoachAuthAlertProperties(false, "", "", ""));
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-10T10:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
