package com.jllado.weightcontrol.api.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeCreateRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeUpdateRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.CoachBackPainEpisodeRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.CoachBackPainEpisodeUpdateRequest;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.MoodPeriod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BackPainEpisodeDtosTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void painFreeEntriesRequireNoLocationAndCoachConfirmation() {
        assertTrue(validator.validate(new BackPainEpisodeCreateRequest(LocalDate.now(), MoodPeriod.MORNING, null, null, BackPainSeverity.NONE, null)).isEmpty());
        assertTrue(validator.validate(new BackPainEpisodeUpdateRequest(MoodPeriod.MORNING, null, null, BackPainSeverity.NONE, null)).isEmpty());
        assertFalse(validator.validate(new BackPainEpisodeCreateRequest(LocalDate.now(), MoodPeriod.MORNING, BackRegion.LOWER, null, BackPainSeverity.NONE, null)).isEmpty());
        assertFalse(validator.validate(new BackPainEpisodeUpdateRequest(MoodPeriod.MORNING, null, BackSide.LEFT, BackPainSeverity.NONE, null)).isEmpty());
        assertTrue(validator.validate(new CoachBackPainEpisodeRequest(LocalDate.now(), MoodPeriod.MORNING, null, null, BackPainSeverity.NONE, null, true)).isEmpty());
        assertFalse(validator.validate(new CoachBackPainEpisodeRequest(LocalDate.now(), MoodPeriod.MORNING, null, null, BackPainSeverity.NONE, null, false)).isEmpty());
        assertTrue(validator.validate(new CoachBackPainEpisodeUpdateRequest(MoodPeriod.MORNING, null, null, BackPainSeverity.NONE, null, true)).isEmpty());
        assertFalse(validator.validate(new CoachBackPainEpisodeUpdateRequest(MoodPeriod.MORNING, null, null, BackPainSeverity.NONE, null, false)).isEmpty());
        assertFalse(validator.validate(new CoachBackPainEpisodeUpdateRequest(MoodPeriod.MORNING, null, null, BackPainSeverity.MILD, null, true)).isEmpty());
    }

    @Test
    void acceptsSeverityValues() {
        BackPainEpisodeCreateRequest mild = new BackPainEpisodeCreateRequest(LocalDate.now(), MoodPeriod.MORNING, BackRegion.LOWER, BackSide.LEFT, BackPainSeverity.MILD, null);
        BackPainEpisodeUpdateRequest extreme = new BackPainEpisodeUpdateRequest(MoodPeriod.EVENING, BackRegion.UPPER, BackSide.RIGHT, BackPainSeverity.EXTREME, "Sharp pain");

        assertTrue(validator.validate(mild).isEmpty());
        assertTrue(validator.validate(extreme).isEmpty());
    }

    @Test
    void rejectsMissingSeverity() {
        BackPainEpisodeCreateRequest request = new BackPainEpisodeCreateRequest(LocalDate.now(), MoodPeriod.MIDDAY, BackRegion.LOWER, BackSide.LEFT, null, null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingLocation() {
        BackPainEpisodeCreateRequest missingRegion = new BackPainEpisodeCreateRequest(LocalDate.now(), MoodPeriod.MIDDAY, null, BackSide.CENTER, BackPainSeverity.MODERATE, null);
        BackPainEpisodeUpdateRequest missingSide = new BackPainEpisodeUpdateRequest(MoodPeriod.MIDDAY, BackRegion.MIDDLE, null, BackPainSeverity.MODERATE, null);

        assertFalse(validator.validate(missingRegion).isEmpty());
        assertFalse(validator.validate(missingSide).isEmpty());
    }

    @Test
    void rejectsLongNote() {
        BackPainEpisodeCreateRequest request = new BackPainEpisodeCreateRequest(LocalDate.now(), MoodPeriod.MIDDAY, BackRegion.MIDDLE, BackSide.CENTER, BackPainSeverity.MODERATE, "N".repeat(501));

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingPeriod() {
        BackPainEpisodeCreateRequest request = new BackPainEpisodeCreateRequest(LocalDate.now(), null, BackRegion.MIDDLE, BackSide.CENTER, BackPainSeverity.MODERATE, null);

        assertFalse(validator.validate(request).isEmpty());
    }
}
