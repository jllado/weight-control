package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.ActionDtos.ActionRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
class ActionPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false) static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("actions"); }
    }
    @Autowired ActionService actions;
    @Autowired UserRepository users;
    @Autowired com.jllado.weightcontrol.repository.CoachingPlanRepository plans;
    @Autowired com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository summaries;
    @Autowired com.jllado.weightcontrol.repository.WeeklyReflectionRepository reflections;
    @Autowired ActionRecommendationService recommendations;

    @Test void sourcesAreOwnedStableAcrossReorderingAndRejectReplacedText() throws Exception {
        var owner = user("source-owner");
        var other = user("source-other");
        var plan = new com.jllado.weightcontrol.domain.CoachingPlan();
        plan.setUser(owner); plan.setGoal("Goal"); plan.setStartDate(LocalDate.now());
        plan.setPrinciples(java.util.List.of()); plan.setPriorities(java.util.List.of());
        plan.setActions(java.util.List.of("No phone in bed", "Walk daily"));
        plans.save(plan);
        var source = recommendations.suggestions(owner).getFirst();
        assertThrows(BadRequestException.class, () -> recommendations.resolve(other, source.sourceKey()));
        var request = new ActionRequest("My edited title", LocalDate.now(), 14, source.sourceKey());
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(java.util.List.of(
                () -> actions.create(owner, request).id(), () -> actions.create(owner, request).id()));
            assertEquals(results.getFirst().get(), results.getLast().get());
        }
        plan.setActions(java.util.List.of("Walk daily", "NO PHONE IN BED"));
        plans.save(plan);
        assertEquals(source.sourceKey(), recommendations.suggestions(owner).getLast().sourceKey());
        assertEquals("My edited title", actions.create(owner, request).wording());
        plan.setActions(java.util.List.of("Walk daily", "Different advice"));
        plans.save(plan);
        assertThrows(BadRequestException.class, () -> recommendations.resolve(owner, source.sourceKey()));
    }

    @Test void olderWeeklyReportSourceSurvivesLeavingSuggestionsButNotReflectionReplacement() {
        var owner = user("weekly-source");
        var older = reflection(owner, LocalDate.of(2026, 10, 9));
        var source = recommendations.weekly(owner, older).getFirst();
        reflection(owner, LocalDate.of(2026, 10, 16));
        assertFalse(recommendations.suggestions(owner).stream().anyMatch(row -> row.sourceKey().equals(source.sourceKey())));
        assertEquals(source.wording(), recommendations.resolve(owner, source.sourceKey()).wording());
        older.setNextWeekActions(java.util.List.of("Replaced next week action"));
        reflections.save(older);
        assertThrows(BadRequestException.class, () -> recommendations.resolve(owner, source.sourceKey()));
    }

    private com.jllado.weightcontrol.domain.WeeklyReflection reflection(User owner, LocalDate date) {
        var summary = new com.jllado.weightcontrol.domain.SavedWeeklySummary();
        summary.setUser(owner); summary.setFridayDate(date); summary.setCreatedAt(java.time.Instant.now()); summary.setSnapshotJson("{}");
        summaries.save(summary);
        var reflection = new com.jllado.weightcontrol.domain.WeeklyReflection();
        reflection.setWeeklySummary(summary); reflection.setGeneratedAt(java.time.Instant.now()); reflection.setModel("ChatGPT");
        reflection.setTitle("Week"); reflection.setSummary("Findings");
        reflection.setBodyCompositionSummary("Finding"); reflection.setBodyCompositionNextAction("Body action");
        reflection.setBloodPressureSummary("Finding"); reflection.setBloodPressureNextAction("BP action");
        reflection.setRoutinesSummary("Finding"); reflection.setRoutinesNextAction("Routine action");
        reflection.setNutritionSummary("Finding"); reflection.setNutritionNextAction("Nutrition action");
        reflection.setTrainingRecoverySummary("Finding"); reflection.setTrainingRecoveryNextAction("Training action");
        reflection.setGoalProgressSummary("Finding"); reflection.setGoalProgressNextAction("Goal action");
        reflection.setNextWeekActions(java.util.List.of("Original next week action"));
        return reflections.save(reflection);
    }

    @Test void attemptsKeepCorrectionsLocksOwnerIsolationAndIndependentRestarts() {
        User owner = user("action-owner");
        User other = user("action-other");
        LocalDate today = LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE);
        var request = new ActionRequest("No phone in bed", today.minusDays(2), 3, null);
        var attempt = actions.create(owner, request);
        assertEquals("ACTIVE", attempt.status());
        actions.check(owner, attempt.id(), today.minusDays(2), true);
        actions.check(owner, attempt.id(), today.minusDays(2), true);
        actions.check(owner, attempt.id(), today.minusDays(1), true);
        assertEquals(2, actions.find(owner, attempt.id(), today).streak());
        var completed = actions.check(owner, attempt.id(), today, true);
        assertEquals("COMPLETED", completed.status());
        assertEquals(3, completed.checkinDates().size());
        assertTrue(actions.list(owner, today, "ACTIVE").stream().anyMatch(row -> row.id().equals(attempt.id())));
        assertThrows(NotFoundException.class, () -> actions.check(other, attempt.id(), today, true));
        assertThrows(BadRequestException.class, () -> actions.check(owner, attempt.id(), today.plusDays(1), true));
        assertThrows(BadRequestException.class, () -> actions.check(owner, attempt.id(), today.minusDays(3), true));
        actions.check(owner, attempt.id(), today.minusDays(1), false);
        assertEquals("ACTIVE", actions.find(owner, attempt.id(), today).status());
        assertEquals(2, actions.find(owner, attempt.id(), today).checkinDates().size());
        assertThrows(BadRequestException.class, () -> actions.update(owner, attempt.id(), new ActionRequest("Changed", today, 2, null)));
        actions.update(owner, attempt.id(), new ActionRequest("Changed wording", today.minusDays(2), 3, null));
        actions.stop(owner, attempt.id());
        assertEquals("STOPPED", actions.check(owner, attempt.id(), today.minusDays(1), true).status());
        assertEquals(1, actions.list(owner, today, "HISTORY").size());
        var restarted = actions.restart(owner, attempt.id(), new ActionRequest("Changed wording", today, 3, null));
        assertNotEquals(attempt.id(), restarted.id());
        assertEquals(0, restarted.streak());
        assertEquals(3, actions.find(owner, attempt.id(), today).checkinDates().size());
    }
    private User user(String name) { var user = new User(); user.setEmail(name + "@example.com"); return users.save(user); }
}
