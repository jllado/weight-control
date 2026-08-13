package com.jllado.weightcontrol.api.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeCreateRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeUpdateRequest;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BackPainEpisodeDtosTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsSeverityValues() {
        BackPainEpisodeCreateRequest mild = new BackPainEpisodeCreateRequest(LocalDate.now(), BackRegion.LOWER, BackSide.LEFT, BackPainSeverity.MILD, null);
        BackPainEpisodeUpdateRequest extreme = new BackPainEpisodeUpdateRequest(BackRegion.UPPER, BackSide.RIGHT, BackPainSeverity.EXTREME, "Sharp pain");

        assertTrue(validator.validate(mild).isEmpty());
        assertTrue(validator.validate(extreme).isEmpty());
    }

    @Test
    void rejectsMissingSeverity() {
        BackPainEpisodeCreateRequest request = new BackPainEpisodeCreateRequest(LocalDate.now(), BackRegion.LOWER, BackSide.LEFT, null, null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingLocation() {
        BackPainEpisodeCreateRequest missingRegion = new BackPainEpisodeCreateRequest(LocalDate.now(), null, BackSide.CENTER, BackPainSeverity.MODERATE, null);
        BackPainEpisodeUpdateRequest missingSide = new BackPainEpisodeUpdateRequest(BackRegion.MIDDLE, null, BackPainSeverity.MODERATE, null);

        assertFalse(validator.validate(missingRegion).isEmpty());
        assertFalse(validator.validate(missingSide).isEmpty());
    }

    @Test
    void rejectsLongNote() {
        BackPainEpisodeCreateRequest request = new BackPainEpisodeCreateRequest(LocalDate.now(), BackRegion.MIDDLE, BackSide.CENTER, BackPainSeverity.MODERATE, "N".repeat(501));

        assertFalse(validator.validate(request).isEmpty());
    }
}
