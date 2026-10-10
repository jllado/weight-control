package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jllado.weightcontrol.api.dto.CoachDtos.ReflectionsContext;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.*;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.DashboardReflectionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {"app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example", "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"})
class ReflectionSectionsPersistenceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection MariaDBContainer<?> database() {
            return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("reflection_sections");
        }
    }

    @Autowired DashboardReflectionService reflections;
    @Autowired DailyStatusSnapshotService snapshots;
    @Autowired HealthDataContextService context;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @Autowired DashboardReflectionRepository records;

    @Test
    void savesReadsReplacesAndRejectsIncompleteWritesWithoutChangingPriorEvidence() throws Exception {
        var date = LocalDate.of(2026, 8, 20);
        var owner = user("reflection-owner@example.com", date.plusDays(1));
        var other = user("reflection-other@example.com", date.plusDays(1));
        snapshots.getOrBuild(owner, date);
        var meals = new ReflectionSection("Macros are partial in the recorded meals.", "Record portions at lunch.");
        var workouts = new ReflectionSection("Two comparable strength sessions were recorded.", "Repeat the planned strength session.");
        var saved = reflections.save(owner, date, request(meals, workouts));
        var read = ReflectionResponse.from(reflections.find(owner, date).orElseThrow());
        assertEquals(meals, read.meals());
        assertEquals(workouts, read.workouts());
        assertEquals(7, read.planProgressScore());
        assertEquals(List.of("Mood improved"), read.positiveSignals());
        assertTrue(reflections.find(other, date).isEmpty());
        assertThrows(BadRequestException.class, () -> reflections.save(owner, date.plusDays(2), request(meals, workouts)));

        var recent = context.getReflectionContext(owner, date.plusDays(1)).recentReflections().getFirst();
        assertEquals(meals, recent.meals());
        assertEquals(workouts, recent.workouts());
        assertTrue(context.getReflectionContext(owner, date).recentReflections().isEmpty());
        var coach = (ReflectionsContext) context.getHealthContext(owner, date, date, Set.of(CoachDomain.REFLECTIONS), OffsetDateTime.now()).data().get(CoachDomain.REFLECTIONS);
        assertEquals(meals, coach.reflections().getFirst().meals());
        assertFalse(json.writeValueAsString(coach).contains(owner.getEmail()));
        assertFalse(json.writeValueAsString(coach).contains("\"id\""));
        var privateContext = (ReflectionsContext) context.getHealthContext(other, date, date, Set.of(CoachDomain.REFLECTIONS), OffsetDateTime.now()).data().get(CoachDomain.REFLECTIONS);
        assertTrue(privateContext.reflections().isEmpty());

        var replacement = new ReflectionSection("Portions remain partly recorded.", "Add a portion note.");
        assertEquals(saved.getId(), reflections.save(owner, date, request(replacement, workouts)).getId());
        var prior = json.writeValueAsString(ReflectionResponse.from(reflections.find(owner, date).orElseThrow()));
        var valid = new ReflectionSection("No evidence recorded.", "Record evidence.");
        for (var invalid : java.util.Arrays.asList(null,
            new ReflectionSection(" ", "Action"), new ReflectionSection("Summary", " "),
            new ReflectionSection(null, "Action"), new ReflectionSection("Summary", null),
            new ReflectionSection("S".repeat(201), "Action"), new ReflectionSection("Summary", "A".repeat(121)))) {
            assertThrows(BadRequestException.class, () -> reflections.save(owner, date, request(invalid, valid)));
            assertThrows(BadRequestException.class, () -> reflections.save(owner, date, request(valid, invalid)));
            assertEquals(prior, json.writeValueAsString(ReflectionResponse.from(reflections.find(owner, date).orElseThrow())));
        }
        var compact = request(valid, valid);
        var tooLong = new SaveReflectionRequest(compact.title(), compact.summary(), compact.planProgressScore(),
            compact.planProgressRationale(), compact.positiveSignals(), compact.watchouts(), List.of("A".repeat(128)), valid, valid);
        assertThrows(BadRequestException.class, () -> reflections.save(owner, date, tooLong));
        assertEquals(prior, json.writeValueAsString(ReflectionResponse.from(reflections.find(owner, date).orElseThrow())));
        var omitted = json.readValue("""
            {"title":"Legacy client","summary":"Summary","positiveSignals":["Positive"],"watchouts":["Watch"],"nextActions":["Action"]}
            """, SaveReflectionRequest.class);
        assertThrows(BadRequestException.class, () -> reflections.save(owner, date, omitted));
        assertEquals(prior, json.writeValueAsString(ReflectionResponse.from(reflections.find(owner, date).orElseThrow())));

        // Simulate an existing pre-section record; read contracts remain compatible.
        var legacy = reflections.find(owner, date).orElseThrow();
        legacy.setMealsSummary(null);
        legacy.setMealsNextAction(null);
        legacy.setWorkoutsSummary(null);
        legacy.setWorkoutsNextAction(null);
        records.saveAndFlush(legacy);
        read = ReflectionResponse.from(reflections.find(owner, date).orElseThrow());
        assertNull(read.meals());
        assertNull(read.workouts());
        assertEquals(1, reflections.getOverview(owner).reflections().size());
    }

    private User user(String email, LocalDate completed) {
        var user = new User();
        user.setEmail(email);
        user.setLastCompletedDashboardDate(completed);
        return users.save(user);
    }

    private SaveReflectionRequest request(ReflectionSection meals, ReflectionSection workouts) {
        return new SaveReflectionRequest("Steady progress", "Review of recorded evidence.", 7, "Agreed actions are progressing.",
            List.of("Mood improved"), List.of("Sleep varies"), List.of("Keep a regular bedtime"), meals, workouts);
    }
}
