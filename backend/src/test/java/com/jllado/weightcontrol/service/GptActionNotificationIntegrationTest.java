package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.domain.InAppNotificationType;
import com.jllado.weightcontrol.domain.PushSubscription;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.InAppNotificationRepository;
import com.jllado.weightcontrol.repository.PushSubscriptionRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:gptnotifications;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.auth.google-client-id=test-client-id", "app.push.enabled=true",
    "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"
})
class GptActionNotificationIntegrationTest {

    @Autowired private GptActionNotificationService actions;
    @Autowired private InAppNotificationService notifications;
    @Autowired private InAppNotificationRepository repository;
    @Autowired private UserRepository users;
    @Autowired private PushSubscriptionRepository subscriptions;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private PushGateway gateway;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user = users.save(user);
        PushSubscription subscription = new PushSubscription();
        subscription.setUser(user);
        subscription.setEndpoint("https://push.example/" + user.getId());
        subscription.setEndpointHash(UUID.randomUUID().toString());
        subscription.setP256dh("key");
        subscription.setAuth("auth");
        subscriptions.save(subscription);
    }

    @Test
    void notificationAndMutationCommitBeforePushAndRemainAcrossDays() {
        when(gateway.send(any(), anyString(), anyInt())).thenAnswer(invocation -> {
            assertEquals("Changed", users.findById(user.getId()).orElseThrow().getDisplayName());
            assertFalse(notifications.findPending(user).isEmpty());
            return 201;
        });
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            assertEquals("saved", actions.execute(user, "Sleep saved", "/sleep", () -> {
                user.setDisplayName("Changed");
                users.save(user);
                return "saved";
            }));
            verifyNoInteractions(gateway);
        });
        verify(gateway).send(any(), contains("Sleep saved"), eq(86400));
        var pending = repository.findPending(user, LocalDate.now().plusDays(2), OffsetDateTime.now().plusDays(2), Set.of(InAppNotificationType.GPT_ACTION));
        assertEquals(1, pending.size());
        assertEquals("/sleep", pending.getFirst().getActionUrl());
        assertEquals(InAppNotificationType.GPT_ACTION, pending.getFirst().getType());
        User other = new User();
        other.setEmail(UUID.randomUUID() + "@example.com");
        other = users.save(other);
        assertTrue(notifications.findPending(other).isEmpty());
        User otherUser = other;
        assertThrows(NotFoundException.class, () -> notifications.dismiss(otherUser, pending.getFirst().getId()));
        notifications.dismiss(user, pending.getFirst().getId());
        assertTrue(notifications.findPending(user).isEmpty());
    }

    @Test
    void outerRollbackRemovesMutationAndNotificationWithoutPush() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            actions.execute(user, "Sleep updated", "/sleep", () -> {
                user.setDisplayName("Rolled back");
                return users.save(user);
            });
            status.setRollbackOnly();
        });
        assertNull(users.findById(user.getId()).orElseThrow().getDisplayName());
        assertTrue(notifications.findPending(user).isEmpty());
        verifyNoInteractions(gateway);
    }

    @Test
    void failedMutationProducesNoNotification() {
        assertThrows(BadRequestException.class, () -> actions.execute(user, "Meal deleted", "/calories", () -> {
            throw new BadRequestException("Rejected");
        }));
        assertTrue(notifications.findPending(user).isEmpty());
        verifyNoInteractions(gateway);
    }

    @Test
    void repeatedUpdatesHaveDistinctNotificationsAndPushFailurePreservesTheWrite() {
        when(gateway.send(any(), anyString(), anyInt())).thenThrow(new PushDeliveryException("Unavailable"));
        for (int i = 0; i < 2; i++) {
            actions.execute(user, "Sleep updated", "/sleep", () -> {
                user.setDisplayName("Saved");
                return users.save(user);
            });
        }
        var pending = notifications.findPending(user);
        assertEquals(2, pending.size());
        assertNotEquals(pending.get(0).getDeduplicationKey(), pending.get(1).getDeduplicationKey());
        assertEquals("Saved", users.findById(user.getId()).orElseThrow().getDisplayName());
        verify(gateway, times(2)).send(any(), contains("Sleep updated"), eq(86400));
    }
}
