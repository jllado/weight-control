package com.jllado.weightcontrol.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.SavedWeeklySummaryRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WorkoutRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklySessionCountRepairService {
    private static final List<String> PERIODS = List.of("currentPeriod", "previousComparablePeriod", "yearAgoComparablePeriod");
    private final UserRepository users;
    private final SavedWeeklySummaryRepository summaries;
    private final WorkoutRepository workouts;
    private final WeeklyMetricsCalculator calculator;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    public WeeklySessionCountRepairService(UserRepository users, SavedWeeklySummaryRepository summaries,
        WorkoutRepository workouts, WeeklyMetricsCalculator calculator, ObjectMapper mapper, JdbcTemplate jdbc) {
        this.users = users;
        this.summaries = summaries;
        this.workouts = workouts;
        this.calculator = calculator;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Transactional
    public RepairResult repair(User owner, LocalDate friday, boolean apply) {
        if (friday.getDayOfWeek() != DayOfWeek.FRIDAY) throw new IllegalArgumentException("Repair date must be a Friday");
        users.findByIdForUpdate(owner.getId()).orElseThrow();
        var summary = summaries.findByUserAndFridayDateForUpdate(owner, friday)
            .orElseThrow(() -> new IllegalArgumentException("No saved summary for the selected owner and Friday"));
        try {
            ObjectNode snapshot = (ObjectNode) mapper.readTree(summary.getSnapshotJson());
            int version = snapshot.required("schemaVersion").intValue();
            if (version == WeeklySummaryService.SNAPSHOT_SCHEMA_VERSION) return new RepairResult(false, List.of());
            if (version != 6) throw new IllegalArgumentException("Session-count repair requires a schema v6 summary");
            List<PeriodCounts> changes = new ArrayList<>();
            for (String name : PERIODS) {
                ObjectNode period = (ObjectNode) snapshot.required("progress").required(name);
                LocalDate start = LocalDate.parse(period.required("startDate").textValue());
                LocalDate end = LocalDate.parse(period.required("endDate").textValue());
                var counts = calculator.summarizeWorkouts(workouts.findByUserAndWorkoutDateBetweenOrderByWorkoutDateAsc(owner, start, end));
                ObjectNode saved = (ObjectNode) period.required("workouts");
                changes.add(new PeriodCounts(start, end, saved.required("strengthSessionCount").intValue(),
                    saved.required("cardioSessionCount").intValue(), saved.required("stretchingSessionCount").intValue(),
                    counts.strengthSessionCount(), counts.cardioSessionCount(), counts.stretchingSessionCount()));
                saved.put("strengthSessionCount", counts.strengthSessionCount());
                saved.put("cardioSessionCount", counts.cardioSessionCount());
                saved.put("stretchingSessionCount", counts.stretchingSessionCount());
            }
            snapshot.put("schemaVersion", WeeklySummaryService.SNAPSHOT_SCHEMA_VERSION);
            if (apply) {
                // Only this explicit administrative repair bypasses the entity's immutable snapshot mapping.
                jdbc.update("update weekly_summaries set snapshot_json = ? where id = ? and user_id = ?",
                    mapper.writeValueAsString(snapshot), summary.getId(), owner.getId());
            }
            return new RepairResult(true, List.copyOf(changes));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read or write the saved weekly snapshot", exception);
        }
    }

    public record RepairResult(boolean changeRequired, List<PeriodCounts> periods) { }
    public record PeriodCounts(LocalDate start, LocalDate end, int oldStrength, int oldCardio, int oldStretching,
        int strength, int cardio, int stretching) { }
}
