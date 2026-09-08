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
        T result = mutation.get();
        var notification = notifications.recordGptAction(user, message, actionUrl);
        events.publishEvent(new GptActionCompleted(user.getId(), notification.getTitle(), message, actionUrl, notification.getDeduplicationKey()));
        return result;
    }

    public record GptActionCompleted(Long userId, String title, String message, String actionUrl, String key) {
    }
}
