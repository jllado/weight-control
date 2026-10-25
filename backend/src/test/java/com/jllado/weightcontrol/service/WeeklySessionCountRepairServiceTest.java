package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jllado.weightcontrol.domain.*;
import com.jllado.weightcontrol.repository.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MariaDBContainer;

@SpringBootTest(properties = {
    "app.auth.google-client-id=test-client-id", "app.chat-gpt-actions.public-base-url=https://test.example",
    "app.chat-gpt-actions.file-signing-secret=test-file-signing-secret-32-bytes-long",
    "app.weekly-summary.scheduling-enabled=false"
})
class WeeklySessionCountRepairServiceTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean @ServiceConnection
        MariaDBContainer<?> database() { return new MariaDBContainer<>("mariadb:11.8").withDatabaseName("weekly_count_repair"); }
    }

    @Autowired private UserRepository users;
    @Autowired private SavedWeeklySummaryRepository summaries;
    @Autowired private ExerciseRepository exercises;
    @Autowired private WorkoutRepository workouts;
    @Autowired private WeeklySessionCountRepairService repair;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    private User owner;
    private final LocalDate friday = LocalDate.of(2026, 10, 23);

    @BeforeEach
    void createOwner() {
        owner = new User();
        owner.setEmail(UUID.randomUUID() + "@example.com");
        owner = users.saveAndFlush(owner);
    }

    @Test
    void dryRunThenApplyChangesOnlyCountsAndVersionAndRerunPreservesSnapshot() throws Exception {
        var saved = saved(owner, friday);
        var older = saved(owner, friday.minusWeeks(1));
        User other = new User();
        other.setEmail(UUID.randomUUID() + "@example.com");
        other = users.saveAndFlush(other);
        var otherSummary = saved(other, friday);
        addMixedWorkout(owner, friday.minusDays(6));
        addMixedWorkout(owner, friday);
        addMixedWorkout(owner, friday.minusDays(7));
        addMixedWorkout(owner, friday.minusDays(364));
        addMixedWorkout(owner, friday.plusDays(1));
        addMixedWorkout(other, friday);
        jdbc.update("""
            insert into weekly_reflections (weekly_summary_id, generated_at, model, title, summary,
            body_composition_summary, body_composition_next_action, blood_pressure_summary, blood_pressure_next_action,
            routines_summary, routines_next_action, nutrition_summary, nutrition_next_action,
            training_recovery_summary, training_recovery_next_action, goal_progress_summary, goal_progress_next_action, next_week_actions_json)
            values (?, '2026-10-24 08:00:00', 'test', 'Keep reflection', 'Saved reflection',
            'Body', 'Body action', 'Pressure', 'Pressure action', 'Routines', 'Routine action', 'Nutrition', 'Nutrition action',
            'Training', 'Training action', 'Goal', 'Goal action', '["Keep this action"]')
            """, saved.getId());
        var reflectionBefore = jdbc.queryForMap("select * from weekly_reflections where weekly_summary_id = ?", saved.getId());
        String before = read(saved);

        var preview = repair.repair(owner, friday, false);
        assertTrue(preview.changeRequired());
        assertEquals(3, preview.periods().size());
        assertEquals(2, preview.periods().getFirst().cardio());
        assertEquals(before, read(saved));

        assertTrue(repair.repair(owner, friday, true).changeRequired());
        ObjectNode expected = (ObjectNode) mapper.readTree(before);
        expected.put("schemaVersion", 7);
        for (var entry : List.of("currentPeriod", "previousComparablePeriod", "yearAgoComparablePeriod")) {
            int count = entry.equals("currentPeriod") ? 2 : 1;
            ObjectNode period = (ObjectNode) expected.at("/progress/" + entry + "/workouts");
            period.put("strengthSessionCount", count);
            period.put("cardioSessionCount", count);
            period.put("stretchingSessionCount", count);
        }
        assertEquals(expected, mapper.readTree(read(saved)));
        var reloaded = summaries.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getCreatedAt(), reloaded.getCreatedAt());
        assertEquals(saved.getScheduledEmailedAt(), reloaded.getScheduledEmailedAt());
        assertEquals(older.getSnapshotJson(), read(older));
        assertEquals(otherSummary.getSnapshotJson(), read(otherSummary));
        assertEquals(reflectionBefore, jdbc.queryForMap("select * from weekly_reflections where weekly_summary_id = ?", saved.getId()));
        String after = read(saved);
        addMixedWorkout(owner, friday);
        assertFalse(repair.repair(owner, friday, true).changeRequired());
        assertEquals(after, read(saved));
    }

    @Test
    void rejectsUnsupportedSnapshotOrWrongOwnerWithoutChangingAnySnapshot() throws Exception {
        var saved = saved(owner, friday);
        ObjectNode invalid = (ObjectNode) mapper.readTree(saved.getSnapshotJson());
        invalid.put("schemaVersion", 5);
        jdbc.update("update weekly_summaries set snapshot_json = ? where id = ?", mapper.writeValueAsString(invalid), saved.getId());
        String before = read(saved);
        User other = new User();
        other.setEmail(UUID.randomUUID() + "@example.com");
        User otherOwner = users.saveAndFlush(other);
        assertThrows(IllegalArgumentException.class, () -> repair.repair(otherOwner, friday, true));
        assertThrows(IllegalArgumentException.class, () -> repair.repair(owner, friday, true));
        assertThrows(IllegalArgumentException.class, () -> repair.repair(owner, friday.minusWeeks(1), true));
        assertThrows(IllegalArgumentException.class, () -> repair.repair(owner, friday.plusDays(1), true));
        assertEquals(before, read(saved));
    }

    @Test
    void invalidLaterPeriodLeavesEarlierCountsUnchanged() throws Exception {
        var saved = saved(owner, friday);
        addMixedWorkout(owner, friday);
        ObjectNode invalid = (ObjectNode) mapper.readTree(saved.getSnapshotJson());
        ((ObjectNode) invalid.at("/progress/yearAgoComparablePeriod")).put("endDate", "invalid");
        jdbc.update("update weekly_summaries set snapshot_json = ? where id = ?", mapper.writeValueAsString(invalid), saved.getId());
        String before = read(saved);
        assertThrows(java.time.format.DateTimeParseException.class, () -> repair.repair(owner, friday, true));
        assertEquals(before, read(saved));
    }

    private String read(SavedWeeklySummary saved) {
        return jdbc.queryForObject("select snapshot_json from weekly_summaries where id = ?", String.class, saved.getId());
    }

    private SavedWeeklySummary saved(User user, LocalDate date) throws Exception {
        ObjectNode snapshot = mapper.createObjectNode();
        snapshot.put("schemaVersion", 6);
        snapshot.put("untouchedFutureField", "preserve exact content");
        snapshot.putObject("nutrition").put("recordedCalories", 1234);
        ObjectNode progress = snapshot.putObject("progress");
        for (String name : List.of("currentPeriod", "previousComparablePeriod", "yearAgoComparablePeriod")) {
            LocalDate end = name.equals("currentPeriod") ? date : date.minusDays(name.equals("previousComparablePeriod") ? 7 : 364);
            ObjectNode period = progress.putObject(name);
            period.put("startDate", end.minusDays(6).toString());
            period.put("endDate", end.toString());
            ObjectNode counts = period.putObject("workouts");
            counts.put("strengthSessionCount", 1);
            counts.put("cardioSessionCount", 0);
            counts.put("stretchingSessionCount", 0);
            counts.put("activeCardioSeconds", 999);
            counts.put("cardioDurationComplete", false);
            counts.put("workoutCount", 42);
        }
        SavedWeeklySummary saved = new SavedWeeklySummary();
        saved.setUser(user);
        saved.setFridayDate(date);
        saved.setCreatedAt(Instant.parse("2026-10-24T06:00:00Z"));
        saved.setScheduledEmailedAt(Instant.parse("2026-10-24T08:00:00Z"));
        saved.setSnapshotJson(mapper.writeValueAsString(snapshot));
        return summaries.saveAndFlush(saved);
    }

    private void addMixedWorkout(User user, LocalDate date) {
        Workout workout = new Workout();
        workout.setUser(user);
        workout.setWorkoutDate(date);
        for (int position = 0; position < 3; position++) {
            Exercise exercise = new Exercise();
            exercise.setName(UUID.randomUUID().toString());
            exercise.setDescription("Test exercise");
            exercise.setExerciseType(position == 2 ? ExerciseType.STRETCHING : ExerciseType.TRAINING);
            exercise.setTrackingMode(position == 1 ? ExerciseTrackingMode.CARDIO : ExerciseTrackingMode.SECONDS);
            if (position == 0) exercise.setPrimaryMuscleGroup(PrimaryMuscleGroup.CORE);
            exercise = exercises.saveAndFlush(exercise);
            WorkoutLine line = new WorkoutLine();
            line.setWorkout(workout);
            line.setExercise(exercise);
            line.setPosition(position);
            WorkoutSegment segment = new WorkoutSegment();
            segment.setWorkoutLine(line);
            segment.setPosition(0);
            segment.setDurationSeconds(60);
            line.getSegments().add(segment);
            workout.getLines().add(line);
        }
        workouts.saveAndFlush(workout);
    }
}
