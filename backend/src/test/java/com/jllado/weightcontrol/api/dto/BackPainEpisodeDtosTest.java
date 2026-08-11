package com.jllado.weightcontrol.api.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeCreateRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeUpdateRequest;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BackPainEpisodeDtosTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPainBoundaries() {
        BackPainEpisodeCreateRequest minimum = new BackPainEpisodeCreateRequest(LocalDate.now(), BackRegion.LOWER, BackSide.LEFT, 1, null);
        BackPainEpisodeUpdateRequest maximum = new BackPainEpisodeUpdateRequest(BackRegion.UPPER, BackSide.RIGHT, 10, "Sharp pain");

        assertTrue(validator.validate(minimum).isEmpty());
        assertTrue(validator.validate(maximum).isEmpty());
    }

    @Test
    void rejectsPainOutsideRange() {
        BackPainEpisodeCreateRequest zero = new BackPainEpisodeCreateRequest(LocalDate.now(), BackRegion.LOWER, BackSide.LEFT, 0, null);
        BackPainEpisodeUpdateRequest eleven = new BackPainEpisodeUpdateRequest(BackRegion.UPPER, BackSide.RIGHT, 11, null);

        assertFalse(validator.validate(zero).isEmpty());
        assertFalse(validator.validate(eleven).isEmpty());
    }

    @Test
    void rejectsMissingLocation() {
        BackPainEpisodeCreateRequest missingRegion = new BackPainEpisodeCreateRequest(LocalDate.now(), null, BackSide.CENTER, 5, null);
        BackPainEpisodeUpdateRequest missingSide = new BackPainEpisodeUpdateRequest(BackRegion.MIDDLE, null, 5, null);

        assertFalse(validator.validate(missingRegion).isEmpty());
        assertFalse(validator.validate(missingSide).isEmpty());
    }

    @Test
    void rejectsLongNote() {
        BackPainEpisodeCreateRequest request = new BackPainEpisodeCreateRequest(LocalDate.now(), BackRegion.MIDDLE, BackSide.CENTER, 5, "N".repeat(501));

        assertFalse(validator.validate(request).isEmpty());
    }
}
