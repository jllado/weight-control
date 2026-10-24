package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.api.dto.WeeklyReflectionDtos.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeeklyRecommendationMapperTest {
    @Test void exactNormalizedDuplicatesKeepFirstWordingAndAllProvenance() {
        var reflection = new WeeklyReflectionResponse(LocalDate.of(2026,10,23), Instant.EPOCH, "ChatGPT", "Week", "Summary",
            section("  No phone in bed "), section("Walk daily"), section("NO PHONE IN BED"),
            section("Eat beans"), section("Walk daily"), section("Review goals"),
            List.of("No phone in bed", "Walk daily"));
        var rows = WeeklyRecommendationMapper.from("weekly:42", reflection);
        assertEquals(List.of("No phone in bed", "Walk daily", "Eat beans", "Review goals"),
            rows.stream().map(WeeklyRecommendationMapper.Recommendation::wording).toList());
        assertEquals(List.of("Next week", "Body composition", "Routines"), rows.getFirst().provenance());
        assertFalse(rows.getFirst().sourceKey().contains("phone"));
        var reordered = new WeeklyReflectionResponse(reflection.fridayDate(), Instant.now(), "ChatGPT", "Week", "Summary",
            reflection.bodyComposition(), reflection.bloodPressure(), reflection.routines(), reflection.nutrition(),
            reflection.trainingRecovery(), reflection.goalProgress(), List.of("Walk daily", "No phone in bed"));
        assertEquals(rows.getFirst().sourceKey(), WeeklyRecommendationMapper.from("weekly:42", reordered).get(1).sourceKey());
        assertNotEquals(rows.getFirst().sourceKey(), WeeklyRecommendationMapper.key("weekly:42", "Different action"));
    }
    private WeeklyReflectionSection section(String action) { return new WeeklyReflectionSection("Finding", action); }
}
