package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.User;
import jakarta.transaction.Transactional;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class GptActionNotificationService {

    private final InAppNotificationService notifications;
    private final ApplicationEventPublisher events;

    public GptActionNotificationService(InAppNotificationService notifications, ApplicationEventPublisher events) {
        this.notifications = notifications;
        this.events = events;
    }

    public <T> T execute(User user, String message, String actionUrl, Supplier<T> mutation) {
        T result = CoachActionTiming.time("coachWrite", "mutation", mutation);
        notify(user, message, actionUrl);
        return result;
    }

    public record CreatedResult<T>(T result, boolean created) {}

    public <T> T executeIfCreated(User user, String message, String actionUrl, Supplier<CreatedResult<T>> mutation) {
        CreatedResult<T> outcome = CoachActionTiming.time("coachWrite", "mutation", mutation);
        if (outcome.created()) notify(user, message, actionUrl);
        return outcome.result();
    }

    private void notify(User user, String message, String actionUrl) {
        var notification = CoachActionTiming.time("coachWrite", "notificationRecord", () -> notifications.recordGptAction(user, message, actionUrl));
        CoachActionTiming.time("coachWrite", "eventPublish", () -> {
            events.publishEvent(new GptActionCompleted(user.getId(), notification.getId(), notification.getTitle(), message, actionUrl, notification.getDeduplicationKey()));
            return null;
        });
    }

    public record GptActionCompleted(Long userId, Long notificationId, String title, String message, String actionUrl, String key) {
    }
}
