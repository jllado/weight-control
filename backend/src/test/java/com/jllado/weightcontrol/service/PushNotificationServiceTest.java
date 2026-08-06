package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.PushDtos.PushKeysRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.PushSubscriptionRequest;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.DailyStatus;
import com.jllado.weightcontrol.domain.PushSubscription;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.PushSubscriptionRepository;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private PushSubscriptionRepository repository;
    @Mock
    private DailyStatusSnapshotService snapshotService;
    @Mock
    private PushGateway gateway;
    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("", "test@example.com"),
            new AppProperties.Push(true, "public", "private", "mailto:test@example.com")
        );
        service = new PushNotificationService(repository, snapshotService, gateway, new ObjectMapper(), properties);
    }

    @Test
    void registerReassignsAnExistingBrowserSubscriptionToTheCurrentUser() {
        User previousUser = user(1L);
        User currentUser = user(2L);
        PushSubscription existing = subscription(10L, previousUser, "https://push.example/subscription");
        PushSubscriptionRequest request = new PushSubscriptionRequest(
            existing.getEndpoint(),
            new PushKeysRequest("new-p256dh", "new-auth")
        );
        when(repository.findByEndpointHash(anyString())).thenReturn(Optional.of(existing));

        service.register(currentUser, request);

        assertEquals(currentUser, existing.getUser());
        assertEquals("new-p256dh", existing.getP256dh());
        assertEquals("new-auth", existing.getAuth());
        verify(repository).save(existing);
    }

    @Test
    void unregisterOnlyRemovesTheCurrentUsersDevice() {
        User currentUser = user(1L);
        PushSubscription owned = subscription(10L, currentUser, "https://push.example/owned");
        when(repository.findByEndpointHash(anyString())).thenReturn(Optional.of(owned));

        service.unregister(currentUser, owned.getEndpoint());

        verify(repository).delete(owned);
    }

    @Test
    void unregisterDoesNotRemoveAnotherUsersDevice() {
        User currentUser = user(1L);
        PushSubscription anotherUsers = subscription(10L, user(2L), "https://push.example/other");
        when(repository.findByEndpointHash(anyString())).thenReturn(Optional.of(anotherUsers));

        service.unregister(currentUser, anotherUsers.getEndpoint());

        verify(repository, never()).delete(any());
    }

    @ParameterizedTest
    @CsvSource({
        "0,0,0 routines remaining today.",
        "2,1,1 routine remaining today.",
        "3,1,2 routines remaining today."
    })
    void dailyReminderContainsTheRemainingRoutineCount(int total, int done, String expectedBody) {
        User user = user(1L);
        PushSubscription subscription = subscription(10L, user, "https://push.example/one");
        when(repository.findAll()).thenReturn(List.of(subscription));
        when(snapshotService.rebuild(user, LocalDate.of(2026, 8, 6))).thenReturn(status(total, done));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.DAILY_TTL_SECONDS))).thenReturn(201);

        service.sendDailyReminders(LocalDate.of(2026, 8, 6));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(eq(subscription), payload.capture(), eq(PushNotificationService.DAILY_TTL_SECONDS));
        org.junit.jupiter.api.Assertions.assertTrue(payload.getValue().contains("\"body\":\"" + expectedBody + "\""));
    }

    @Test
    void dailyReminderSendsToEveryDeviceAndRemovesExpiredSubscriptions() {
        User user = user(1L);
        PushSubscription expired = subscription(10L, user, "https://push.example/expired");
        PushSubscription active = subscription(11L, user, "https://push.example/active");
        when(repository.findAll()).thenReturn(List.of(expired, active));
        when(snapshotService.rebuild(user, LocalDate.of(2026, 8, 6))).thenReturn(status(2, 0));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.DAILY_TTL_SECONDS))).thenReturn(410, 201);

        service.sendDailyReminders(LocalDate.of(2026, 8, 6));

        verify(gateway, times(2)).send(any(), anyString(), eq(PushNotificationService.DAILY_TTL_SECONDS));
        verify(repository).delete(expired);
        verify(repository, never()).delete(active);
    }

    @Test
    void dailyReminderContinuesAfterOneDeviceFails() {
        User user = user(1L);
        PushSubscription first = subscription(10L, user, "https://push.example/first");
        PushSubscription second = subscription(11L, user, "https://push.example/second");
        when(repository.findAll()).thenReturn(List.of(first, second));
        when(snapshotService.rebuild(user, LocalDate.of(2026, 8, 6))).thenReturn(status(2, 0));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.DAILY_TTL_SECONDS)))
            .thenThrow(new PushDeliveryException("failed"))
            .thenReturn(201);

        service.sendDailyReminders(LocalDate.of(2026, 8, 6));

        verify(gateway, times(2)).send(any(), anyString(), eq(PushNotificationService.DAILY_TTL_SECONDS));
    }

    @Test
    void testNotificationTargetsTheOwnedDevice() {
        User user = user(1L);
        PushSubscription subscription = subscription(10L, user, "https://push.example/test");
        when(repository.findByEndpointHash(anyString())).thenReturn(Optional.of(subscription));
        when(snapshotService.rebuild(eq(user), any(LocalDate.class))).thenReturn(status(1, 0));
        when(gateway.send(any(), anyString(), eq(PushNotificationService.TEST_TTL_SECONDS))).thenReturn(201);

        service.sendTest(user, subscription.getEndpoint());

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway).send(eq(subscription), payload.capture(), eq(PushNotificationService.TEST_TTL_SECONDS));
        org.junit.jupiter.api.Assertions.assertTrue(payload.getValue().contains("\"title\":\"Routine reminder test\""));
        org.junit.jupiter.api.Assertions.assertTrue(payload.getValue().contains("\"body\":\"1 routine remaining today.\""));
    }

    @Test
    void testNotificationRejectsAnotherUsersDevice() {
        User currentUser = user(1L);
        PushSubscription subscription = subscription(10L, user(2L), "https://push.example/test");
        when(repository.findByEndpointHash(anyString())).thenReturn(Optional.of(subscription));

        assertThrows(NotFoundException.class, () -> service.sendTest(currentUser, subscription.getEndpoint()));

        verifyNoInteractions(gateway);
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static PushSubscription subscription(Long id, User user, String endpoint) {
        PushSubscription subscription = new PushSubscription();
        subscription.setId(id);
        subscription.setUser(user);
        subscription.setEndpoint(endpoint);
        subscription.setEndpointHash("hash-" + id);
        subscription.setP256dh("p256dh");
        subscription.setAuth("auth");
        return subscription;
    }

    private static DailyStatus status(int total, int done) {
        DailyStatus status = new DailyStatus();
        status.setTotalRoutines(total);
        status.setRoutinesDone(done);
        return status;
    }
}
