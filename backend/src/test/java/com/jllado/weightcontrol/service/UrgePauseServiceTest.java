package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.UrgePauseDtos.*;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.*;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.push.enabled=false", "app.auth.jwt-secret=test-jwt-secret-test-jwt-secret-test-jwt-secret", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
class UrgePauseServiceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8"); }
    }
    @Autowired UrgePauseService service;
    @Autowired UrgePauseRepository pauses;
    @Autowired UserRepository users;
    @Autowired DecisionOutcomeRepository decisions;
    @Autowired InAppNotificationService notifications;
    @Autowired HealthDataContextService context;
    @Autowired ObjectMapper mapper;
    @MockitoBean UrgePauseScheduler scheduler;
    @MockitoSpyBean PersonalRecordMutationService mutations;
    private User user;

    @BeforeEach void setup() { user = newUser(); }
    private User newUser() { User value = new User(); value.setEmail(UUID.randomUUID() + "@example.com"); return users.save(value); }
    private PauseResponse start(String description) { return service.start(user, new StartRequest(description)).pause(); }
    private void expire(Long id) {
        var pause = pauses.findById(id).orElseThrow();
        pause.setStartedAt(OffsetDateTime.now().minusMinutes(16)); pause.setEndsAt(OffsetDateTime.now().minusMinutes(1)); pauses.save(pause);
    }

    @Test void persistsAnOptionalDescriptionAndExactlyFifteenMinutes() {
        var first = start("  Chocolate  ");
        assertEquals("Chocolate", first.description());
        assertEquals(Duration.ofMinutes(15), Duration.between(first.startedAt(), first.endsAt()));
        assertEquals(first.id(), start("Another urge").id());
        assertEquals(first.id(), service.current(user).pause().id());
        assertThrows(BadRequestException.class, () -> service.checkIn(user, first.id(), new CheckInRequest(UrgePause.Answer.STILL_WANT)));
        service.cancel(user, first.id());
        assertNull(service.current(user).pause());
        assertNull(start("  ").description());
        assertEquals(2, pauses.countByUser(user));
    }

    @Test void concurrentStartsAndRepeatsKeepOneActiveInterval() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            Callable<Long> task = () -> { gate.await(); return start(null).id(); };
            var first = executor.submit(task); var second = executor.submit(task); gate.countDown();
            Long id = first.get(15, TimeUnit.SECONDS);
            assertEquals(id, second.get(15, TimeUnit.SECONDS));
            assertEquals(1, pauses.countByUser(user));
            expire(id);
            var repeatGate = new CountDownLatch(1);
            Callable<Long> repeat = () -> { repeatGate.await(); return service.repeat(user, id).pause().id(); };
            var a = executor.submit(repeat); var b = executor.submit(repeat); repeatGate.countDown();
            assertEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(2, pauses.countByUser(user));
        }
    }

    @Test void preservesAnswersAcrossRepeatsAndDoesNotInferDecisions() {
        var first = start("Sweets"); expire(first.id());
        service.checkIn(user, first.id(), new CheckInRequest(UrgePause.Answer.STILL_WANT));
        var repeated = service.repeat(user, first.id()).pause();
        assertNotEquals(first.id(), repeated.id());
        assertEquals("Sweets", repeated.description());
        assertNull(repeated.answer());
        expire(repeated.id());
        service.checkIn(user, repeated.id(), new CheckInRequest(UrgePause.Answer.NOT_ANYMORE));
        service.finish(user, repeated.id(), new FinishRequest(null, null));
        assertNull(service.current(user).pause());
        assertTrue(decisions.findByUserOrderByOutcomeDateAscIdAsc(user).isEmpty());
        var history = service.context(user, LocalDate.now(DateTimes.USER_ZONE).minusDays(1), LocalDate.now(DateTimes.USER_ZONE));
        assertEquals(1, history.size()); assertEquals(2, history.getFirst().intervals().size());
        assertEquals(UrgePause.Answer.STILL_WANT, history.getFirst().intervals().getFirst().answer());
        assertEquals(UrgePause.Status.REPEATED, history.getFirst().intervals().getFirst().status());
        assertEquals(UrgePause.Answer.NOT_ANYMORE, history.getFirst().intervals().getLast().answer());
    }

    @Test void completionRetriesRecordOneDecisionAndFailuresRollBack() {
        var pause = start("Cookies"); expire(pause.id());
        doThrow(new IllegalStateException("Save unavailable")).when(mutations).createDecisionOutcome(eq(user), any());
        assertThrows(IllegalStateException.class, () -> service.finish(user, pause.id(), new FinishRequest(DecisionOutcomeType.WIN, "Waited")));
        assertNotNull(service.current(user).pause());
        assertTrue(decisions.findByUserOrderByOutcomeDateAscIdAsc(user).isEmpty());
        doCallRealMethod().when(mutations).createDecisionOutcome(eq(user), any());
        var result = service.finish(user, pause.id(), new FinishRequest(DecisionOutcomeType.WIN, "  Waited  "));
        assertEquals("Waited", result.result().reason());
        assertEquals(LocalDate.now(DateTimes.USER_ZONE), result.result().date());
        assertEquals(result.result().id(), service.finish(user, pause.id(), new FinishRequest(DecisionOutcomeType.WIN, "retry")).result().id());
        assertEquals(1, decisions.findByUserOrderByOutcomeDateAscIdAsc(user).size());
        assertNull(service.current(user).pause());
    }

    @Test void notificationsSurviveMidnightAndAreGenericDeduplicatedAndDismissedByLifecycle() {
        var pause = start("Private craving");
        var stored = pauses.findById(pause.id()).orElseThrow();
        stored.setStartedAt(OffsetDateTime.now().minusDays(1).minusMinutes(15)); stored.setEndsAt(OffsetDateTime.now().minusDays(1)); pauses.save(stored);
        service.notifyDue(pause.id(), OffsetDateTime.now()); service.notifyDue(pause.id(), OffsetDateTime.now());
        var pending = notifications.findPending(user);
        assertEquals(1, pending.size());
        assertFalse(pending.getFirst().getMessage().contains("Private craving"));
        assertEquals("/?urgePauseId=" + pause.id(), pending.getFirst().getActionUrl());
        notifications.dismiss(user, pending.getFirst().getId());
        assertNotNull(service.current(user).pause());
        var next = service.repeat(user, pause.id()).pause(); expire(next.id());
        service.notifyDue(next.id(), OffsetDateTime.now());
        service.cancel(user, next.id());
        assertTrue(notifications.findPending(user).isEmpty());
        service.notifyDue(next.id(), OffsetDateTime.now());
        assertTrue(notifications.findPending(user).isEmpty());
    }

    @Test void rejectsOtherOwnersAndStaleActionsWithoutChangingTheCurrentPause() {
        var pause = start(null); User other = newUser();
        assertThrows(NotFoundException.class, () -> service.cancel(other, pause.id()));
        assertNull(service.current(other).pause());
        service.cancel(user, pause.id()); var next = start(null);
        assertThrows(BadRequestException.class, () -> service.repeat(user, pause.id()));
        assertThrows(BadRequestException.class, () -> service.finish(user, pause.id(), new FinishRequest(null, null)));
        assertEquals(next.id(), service.current(user).pause().id());
    }

    @Test void coachReturnsOnlyOverlappingOwnedIntervalsWithoutIdentifiers() throws Exception {
        var pause = start("Want sweets");
        LocalDate day = LocalDate.now(DateTimes.USER_ZONE).minusDays(2);
        var stored = pauses.findById(pause.id()).orElseThrow();
        stored.setStartedAt(DateTimes.startOfDay(day).minusMinutes(5)); stored.setEndsAt(DateTimes.startOfDay(day).plusMinutes(10));
        stored.setClosedAt(DateTimes.startOfDay(day).plusMinutes(12)); stored.setStatus(UrgePause.Status.FINISHED); pauses.save(stored);
        assertEquals(1, service.context(user, day, day).size());
        assertTrue(service.context(user, day.plusDays(1), day.plusDays(1)).isEmpty());
        assertTrue(service.context(newUser(), day, day).isEmpty());
        var availability = context.getCoachCatalog(user).domains().stream().filter(d -> d.domain() == CoachDomain.BEHAVIOR).findFirst().orElseThrow();
        assertEquals(1, availability.recordCount());
        assertEquals(day.minusDays(1), availability.firstDate());
        String json = mapper.writeValueAsString(context.getHealthContext(user, day, day, Set.of(CoachDomain.BEHAVIOR)));
        assertTrue(json.contains("Want sweets")); assertTrue(json.contains("urgePauses"));
        assertFalse(json.contains("sessionKey")); assertFalse(json.contains("user_id")); assertFalse(json.contains("\"id\"")); assertFalse(json.contains(user.getEmail()));
        assertThrows(BadRequestException.class, () -> context.getHealthContext(user, day.minusDays(91), day, Set.of(CoachDomain.BEHAVIOR)));
    }
}
