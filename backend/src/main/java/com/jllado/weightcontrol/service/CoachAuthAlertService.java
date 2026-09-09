package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.CoachAuthAlertProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CoachAuthAlertService {
    private static final Logger LOG = LoggerFactory.getLogger(CoachAuthAlertService.class);
    private static final Duration INTERVAL = Duration.ofMinutes(15);
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");
    private static final List<String> ROUTES = List.of(
        "/coach/catalog", "/coach/context", "/coach/progress-photos", "/coach/progress-photos/{photoSetId}/files",
        "/coach/meals", "/coach/meals/{id}", "/coach/meals/{id}/delete",
        "/coach/fasting-periods", "/coach/fasting-periods/{id}", "/coach/fasting-periods/{id}/delete",
        "/coach/health-constraints", "/coach/health-constraints/{id}", "/coach/active-plan",
        "/coach/workouts/{workoutDate}/assessment-context", "/coach/workouts/{workoutDate}/assessment",
        "/coach/health-entries/{entryType}", "/coach/health-entries/{entryType}/{id}",
        "/coach/sleeps", "/coach/sleeps/{id}", "/reflections/overview", "/reflections/{date}/context",
        "/reflections/{date}", "/coach/warnings"
    );
    private static final Map<String, Pattern> ROUTE_PATTERNS = routePatterns();
    private final CoachAuthAlertProperties properties;
    private final TelegramAuthAlertSender sender;
    private final Clock clock;
    private final Pattern userAgent;
    private final Map<Failure, Long> pending = new HashMap<>();
    private Instant first;
    private Instant last;
    private Instant nextAttempt = Instant.MIN;

    @Autowired
    public CoachAuthAlertService(CoachAuthAlertProperties properties, TelegramAuthAlertSender sender) {
        this(properties, sender, Clock.systemUTC());
    }

    CoachAuthAlertService(CoachAuthAlertProperties properties, TelegramAuthAlertSender sender, Clock clock) {
        this.properties = properties;
        this.sender = sender;
        this.clock = clock;
        this.userAgent = Pattern.compile("(?:^|[\\s;(])" + Pattern.quote(properties.userAgentProduct()) + "/[^\\s;)]+(?=$|[\\s;)])");
    }

    public void record(String method, String path, String userAgentHeader, Reason reason) {
        String endpoint = ROUTES.stream().filter(route -> ROUTE_PATTERNS.get(route).matcher(path).matches()).findFirst().orElse("OTHER");
        Failure failure = new Failure(METHODS.contains(method) ? method : "OTHER", endpoint, reason);
        Instant now = clock.instant();
        LOG.warn("Coach API authentication failed: time={} method={} endpoint={} reason={}", now, failure.method(), endpoint, reason);
        if (!properties.enabled() || userAgentHeader == null || !userAgent.matcher(userAgentHeader).find()) {
            return;
        }
        synchronized (this) {
            if (pending.isEmpty()) {
                first = now;
            }
            last = now;
            pending.merge(failure, 1L, Long::sum);
        }
    }

    public void deliverPending() {
        Snapshot snapshot;
        synchronized (this) {
            Instant now = clock.instant();
            if (pending.isEmpty() || now.isBefore(nextAttempt)) {
                return;
            }
            snapshot = new Snapshot(first, last, Map.copyOf(pending));
            pending.clear();
            nextAttempt = now.plus(INTERVAL);
        }
        TelegramAuthAlertSender.DeliveryResult result = sender.send(message(snapshot));
        if (!result.delivered()) {
            synchronized (this) {
                first = snapshot.first();
                if (pending.isEmpty()) {
                    last = snapshot.last();
                }
                snapshot.counts().forEach((key, count) -> pending.merge(key, count, Long::sum));
                nextAttempt = clock.instant().plus(result.retryAfter());
            }
        }
    }

    private String message(Snapshot snapshot) {
        long count = snapshot.counts().values().stream().mapToLong(Long::longValue).sum();
        StringBuilder text = new StringBuilder("Weight Control — Coach API authentication failures\n")
            .append("First: ").append(snapshot.first()).append("\nLast: ").append(snapshot.last())
            .append("\nFailures: ").append(count);
        // Keep Telegram text bounded even if every known operation fails in one interval.
        snapshot.counts().entrySet().stream()
            .sorted(Map.Entry.<Failure, Long>comparingByValue().reversed().thenComparing(e -> e.getKey().toString()))
            .limit(10).forEach(entry -> text.append("\n").append(entry.getKey().method()).append(" ")
                .append(entry.getKey().endpoint()).append(" — ").append(entry.getKey().reason()).append(": ").append(entry.getValue()));
        if (snapshot.counts().size() > 10) {
            text.append("\nAdditional groups: ").append(snapshot.counts().size() - 10).append("; see server logs.");
        }
        return text.toString();
    }

    private static Map<String, Pattern> routePatterns() {
        Map<String, Pattern> patterns = new HashMap<>();
        ROUTES.forEach(route -> patterns.put(route, Pattern.compile("/api/chatgpt-actions" + route.replaceAll("\\{[^}]+}", "[^/]+"))));
        return Map.copyOf(patterns);
    }

    public enum Reason { MISSING_HEADER, MALFORMED_BEARER, INVALID_TOKEN, UNCONFIGURED_TOKEN, USER_NOT_FOUND }
    private record Failure(String method, String endpoint, Reason reason) { }
    private record Snapshot(Instant first, Instant last, Map<Failure, Long> counts) { }
}
