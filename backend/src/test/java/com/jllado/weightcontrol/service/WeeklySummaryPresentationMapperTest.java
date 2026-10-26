package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.*;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklySummaryPresentationMapperTest {
    @Test void presentationKeepsSnapshotEvidenceAndCentralizesAllRecommendations() {
        var user = new User();
        var date = LocalDate.of(2026,10,23);
        var input = new WeeklyMetricsCalculator.Input(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var snapshot = new WeeklySummarySnapshot(1, date.minusDays(6), date,
            new WeeklyMetricsCalculator().progress(user, date, input), new WeeklySummarySnapshot.OutcomeMeasurements(null,null),
            List.of(), new WeeklySummarySnapshot.GoalEvidence(false,null,null,null,"Unknown"),
            List.of(new WeeklySummarySnapshot.PersonalRecordSnapshot("Longest session", "Walking", new java.math.BigDecimal("62"),
                com.jllado.weightcontrol.domain.PersonalRecordUnit.SECONDS, date)), List.of());
        var section = new WeeklyReflectionSection("Finding", "Walk daily");
        var reflection = new WeeklyReflectionResponse(date, Instant.EPOCH, "ChatGPT", "Review", "Summary",
            section, section, section, section, section, section, List.of(" WALK DAILY "), null);
        var view = WeeklySummaryPresentationMapper.create(snapshot, reflection, "weekly:42");
        assertEquals(5, view.highlights().size());
        assertEquals("1 new", view.highlights().getLast().value());
        assertTrue(view.highlights().getLast().coverage().contains("Walking"));
        assertTrue(view.highlights().getLast().coverage().contains("2026-10-23"));
        assertEquals("Not recorded", view.highlights().getFirst().value());
        assertEquals(1, view.recommendations().size());
        assertEquals(7, view.recommendations().getFirst().provenance().size());
        assertEquals(1, snapshot.schemaVersion());
        assertNull(snapshot.nutrition());
        assertTrue(WeeklySummaryPresentationMapper.create(snapshot, null, null).recommendations().isEmpty());
    }
}
