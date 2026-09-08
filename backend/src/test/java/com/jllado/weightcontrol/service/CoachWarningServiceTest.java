package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.CoachDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {
    "app.auth.google-client-id=test-client-id",
    "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"
})
class CoachWarningServiceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8"); }
    }
    @Autowired CoachWarningService service;
    @Autowired UserRepository users;
    private User user;
    private final LocalDate date = LocalDate.of(2026, 9, 8);

    @BeforeEach void setup() { user = newUser(); }
    private User newUser() { User value = new User(); value.setEmail(UUID.randomUUID() + "@example.com"); return users.save(value); }
    private WarningContent content(String explanation) { return new WarningContent(explanation, "Sep 1–7: sleeping HR 70 bpm vs 65 bpm in preceding days; seven recorded nights.", "Prioritize consistent sleep.", date.minusDays(7), date); }
    private CreateWarningRequest request(CoachWarningType type) { return new CreateWarningRequest(UUID.randomUUID(), type, content("Possible recovery strain; cause uncertain.")); }

    @Test void preservesRevisionsResolvesIndependentlyAndAllowsRecurrence() {
        var first = service.create(user, request(CoachWarningType.RECOVERY_STRAIN));
        var second = service.create(user, request(CoachWarningType.PAIN_INCREASE));
        var updated = service.update(user, first.id(), new UpdateWarningRequest(first.version(), content("Persistent pattern, with mixed mood evidence.")));
        assertEquals(1, updated.version());
        assertThrows(ResponseStatusException.class, () -> service.update(user, first.id(), new UpdateWarningRequest(first.version(), content("Stale"))));
        service.resolve(user, first.id(), new ResolveWarningRequest(updated.version(), date.plusDays(3), "Three newer recorded nights returned toward baseline."));
        assertEquals(second.id(), service.overview(user).active().getFirst().id());
        assertTrue(service.overview(user).hasHistory());
        assertEquals(CoachWarningStatus.RESOLVED, service.history(user, 0).items().getFirst().status());
        var revisions = service.revisions(user, first.id(), 0).items();
        assertEquals(3, revisions.size());
        assertEquals(first.content(), revisions.getLast().content());
        assertThrows(ResponseStatusException.class, () -> service.update(user, first.id(), new UpdateWarningRequest(2L, content("Cannot reopen"))));
        assertNotEquals(first.id(), service.create(user, request(CoachWarningType.RECOVERY_STRAIN)).id());
    }

    @Test void retriesAreIdempotentAndDuplicateTypesAndForeignAccessAreRejected() {
        var request = request(CoachWarningType.RECOVERY_STRAIN);
        var warning = service.create(user, request);
        assertEquals(warning.id(), service.create(user, request).id());
        assertEquals(1, service.revisions(user, warning.id(), 0).items().size());
        assertThrows(ResponseStatusException.class, () -> service.create(user, new CreateWarningRequest(request.requestKey(), request.type(), content("Different"))));
        assertThrows(ResponseStatusException.class, () -> service.create(user, request(CoachWarningType.RECOVERY_STRAIN)));
        User other = newUser();
        assertTrue(service.overview(other).active().isEmpty());
        assertThrows(NotFoundException.class, () -> service.revisions(other, warning.id(), 0));
        assertThrows(NotFoundException.class, () -> service.update(other, warning.id(), new UpdateWarningRequest(0L, content("Foreign"))));
        assertThrows(NotFoundException.class, () -> service.resolve(other, warning.id(), new ResolveWarningRequest(0L, date, "Foreign")));
    }

    @Test void concurrentCreatesLeaveOneActiveWarningAndOneRevision() throws Exception {
        var request = request(CoachWarningType.SLEEP_DISRUPTION);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.create(user, request));
            var b = pool.submit(() -> service.create(user, request));
            assertEquals(a.get(20, TimeUnit.SECONDS).id(), b.get(20, TimeUnit.SECONDS).id());
        }
        assertEquals(1, service.overview(user).active().size());
    }

    @Test void paginatesHistoryAndDoesNotResurrectResolvedCreateRetries() {
        var original = request(CoachWarningType.HEALTH_CHANGE);
        var warning = service.create(user, original);
        service.resolve(user, warning.id(), new ResolveWarningRequest(0L, date, "New evidence reviewed."));
        assertEquals(CoachWarningStatus.RESOLVED, service.create(user, original).status());
        assertTrue(service.overview(user).active().isEmpty());
        for (int index = 0; index < 10; index++) {
            var next = service.create(user, request(CoachWarningType.HEALTH_CHANGE));
            service.resolve(user, next.id(), new ResolveWarningRequest(0L, date, "Recovery recorded."));
        }
        var firstPage = service.history(user, 0);
        var secondPage = service.history(user, 1);
        assertEquals(10, firstPage.items().size());
        assertTrue(firstPage.hasMore());
        assertEquals(1, secondPage.items().size());
        assertFalse(secondPage.hasMore());
        assertEquals(warning.id(), secondPage.items().getFirst().id());
    }

    @Test void rejectsInvalidChronologyAndPages() {
        var warning = service.create(user, request(CoachWarningType.MOOD_DECLINE));
        assertThrows(ResponseStatusException.class, () -> service.resolve(user, warning.id(), new ResolveWarningRequest(0L, date.minusDays(1), "Older evidence")));
        assertThrows(BadRequestException.class, () -> service.update(user, warning.id(), new UpdateWarningRequest(0L, new WarningContent("X", "Y", "Z", date.plusDays(1), date))));
        assertThrows(BadRequestException.class, () -> service.history(user, -1));
        assertEquals(0, service.overview(user).active().getFirst().version());
    }
}
