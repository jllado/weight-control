package com.jllado.weightcontrol;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.domain.ExerciseTrackingMode;
import com.jllado.weightcontrol.domain.ExerciseType;
import com.jllado.weightcontrol.domain.SavedWeeklySummary;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyReflection;
import com.jllado.weightcontrol.repository.ExerciseRepository;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeeklyReflectionRepository;
import com.jllado.weightcontrol.service.WeeklySummaryService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {
    "app.auth.google-client-id=test-client-id",
    "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long"
})
class MariaDbSchemaValidationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        @ServiceConnection
        MariaDBContainer<?> database() {
            return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("weight_control");
        }
    }

    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private SavedWeeklySummaryRepository weeklySummaryRepository;
    @Autowired private WeeklyReflectionRepository weeklyReflectionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WeeklySummaryService weeklySummaryService;
    @Autowired private com.jllado.weightcontrol.service.NutrientTargetService nutrientTargetService;

    @Test
    void nutrientOverridesPersistPerAccountAndResetToAdultReferences() {
        User owner = persistUser("nutrient-owner@example.com");
        owner.setBirthDate(LocalDate.of(1990, 1, 1));
        owner.setSex(com.jllado.weightcontrol.domain.UserSex.FEMALE);
        owner = userRepository.saveAndFlush(owner);
        User other = persistUser("nutrient-other@example.com");
        LocalDate asOf = LocalDate.of(2026, 8, 12);
        nutrientTargetService.update(owner, new com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetOverrides(
            new java.math.BigDecimal("20.50"), new java.math.BigDecimal("1000"), new java.math.BigDecimal("400")
        ), asOf);

        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        var settings = nutrientTargetService.settings(reloaded, asOf);
        assertEquals(new java.math.BigDecimal("20.50"), settings.overrides().vitaminDMicrograms());
        assertEquals(new java.math.BigDecimal("1000.00"), settings.targets().omega3().value());
        assertEquals(new java.math.BigDecimal("400.00"), settings.targets().magnesium().value());
        assertNull(nutrientTargetService.settings(other, asOf).overrides().vitaminDMicrograms());

        nutrientTargetService.update(reloaded, new com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetOverrides(null, null, null), asOf);
        var reset = nutrientTargetService.settings(userRepository.findById(owner.getId()).orElseThrow(), asOf);
        assertNull(reset.overrides().vitaminDMicrograms());
        assertEquals(new java.math.BigDecimal("15"), reset.targets().vitaminD().value());
        assertEquals(new java.math.BigDecimal("300"), reset.targets().magnesium().value());
        assertNull(reset.targets().omega3().value());
    }

    @Test
    void migrationsMatchTheHibernateSchema() {
        var stretching = exerciseRepository.findAllByOrderByNameAsc().stream().filter(exercise -> exercise.getExerciseType() == ExerciseType.STRETCHING).toList();
        assertEquals(36, stretching.size());
        assertTrue(stretching.stream().allMatch(exercise -> exercise.getTrackingMode() == ExerciseTrackingMode.SECONDS));
        assertTrue(stretching.stream().allMatch(exercise -> !exercise.getDescription().isBlank() && exercise.getDescription().length() <= 500));
    }

    @Test
    void weeklySnapshotsAndReflectionsPersistOwnerScopedWithUniqueWeekAndReflectionConstraints() {
        User owner = persistUser("weekly-owner@example.com");
        User other = persistUser("weekly-other@example.com");
        LocalDate friday = LocalDate.of(2026, 8, 14);
        SavedWeeklySummary summary = persistSummary(owner, friday);

        assertEquals("{\"schemaVersion\":1,\"warnings\":[]}", weeklySummaryRepository.findByUserAndFridayDate(owner, friday).orElseThrow().getSnapshotJson());
        assertTrue(weeklySummaryRepository.findByUserAndFridayDate(other, friday).isEmpty());
        assertThrows(DataIntegrityViolationException.class, () -> persistSummary(owner, friday));

        WeeklyReflection reflection = persistReflection(summary, "A weekly review");
        assertEquals(List.of("Continue"), weeklyReflectionRepository.findByWeeklySummary(summary).orElseThrow().getNextWeekActions());
        assertThrows(DataIntegrityViolationException.class, () -> persistReflection(summary, "Duplicate reflection"));
        assertEquals(reflection.getId(), weeklyReflectionRepository.findByWeeklySummary(summary).orElseThrow().getId());
    }

    @Test
    void concurrentSummaryCreationSerializesOnTheUserAndReturnsOneImmutableSnapshot() throws Exception {
        User owner = persistUser("weekly-concurrent@example.com");
        LocalDate friday = LocalDate.of(2026, 8, 14);
        LocalDate monday = LocalDate.of(2026, 8, 17);

        CompletableFuture<WeeklySummaryService.CreationResult> first = CompletableFuture.supplyAsync(
            () -> weeklySummaryService.createForFridayIfMissing(owner, friday, monday)
        );
        CompletableFuture<WeeklySummaryService.CreationResult> second = CompletableFuture.supplyAsync(
            () -> weeklySummaryService.createForFridayIfMissing(owner, friday, monday)
        );
        var firstResult = first.get(60, TimeUnit.SECONDS);
        var secondResult = second.get(60, TimeUnit.SECONDS);

        assertEquals(firstResult.summary().getId(), secondResult.summary().getId());
        assertEquals(1, List.of(firstResult.created(), secondResult.created()).stream().filter(Boolean::booleanValue).count());
        assertEquals("2026-08-14", weeklySummaryRepository.findByUserAndFridayDate(owner, friday).orElseThrow().getFridayDate().toString());
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        return userRepository.saveAndFlush(user);
    }

    private SavedWeeklySummary persistSummary(User user, LocalDate friday) {
        SavedWeeklySummary summary = new SavedWeeklySummary();
        summary.setUser(user);
        summary.setFridayDate(friday);
        summary.setSnapshotJson("{\"schemaVersion\":1,\"warnings\":[]}");
        summary.setCreatedAt(Instant.parse("2026-08-17T07:00:00Z"));
        return weeklySummaryRepository.saveAndFlush(summary);
    }

    private WeeklyReflection persistReflection(SavedWeeklySummary summary, String title) {
        WeeklyReflection reflection = new WeeklyReflection();
        reflection.setWeeklySummary(summary);
        reflection.setGeneratedAt(Instant.parse("2026-08-17T07:00:00Z"));
        reflection.setModel("ChatGPT");
        reflection.setTitle(title);
        reflection.setSummary("Recorded evidence and uncertainty.");
        reflection.setBodyCompositionSummary("No outcome measurement.");
        reflection.setBodyCompositionNextAction("Review the next saved week.");
        reflection.setBloodPressureSummary("No reading recorded.");
        reflection.setBloodPressureNextAction("Record a reading when appropriate.");
        reflection.setRoutinesSummary("Two of seven eligible days.");
        reflection.setRoutinesNextAction("Keep the agreed routine.");
        reflection.setNutritionSummary("No meals recorded.");
        reflection.setNutritionNextAction("Log meals if useful.");
        reflection.setTrainingRecoverySummary("No workout or sleep data.");
        reflection.setTrainingRecoveryNextAction("Keep recovery records current.");
        reflection.setGoalProgressSummary("Historical goal unavailable.");
        reflection.setGoalProgressNextAction("Review the current plan separately.");
        reflection.setNextWeekActions(List.of("Continue"));
        return weeklyReflectionRepository.saveAndFlush(reflection);
    }
}
