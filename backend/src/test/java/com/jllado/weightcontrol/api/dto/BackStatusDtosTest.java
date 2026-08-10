package com.jllado.weightcontrol.api.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.api.dto.BackStatusDtos.BackRegionStatus;
import com.jllado.weightcontrol.api.dto.BackStatusDtos.BackStatusRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BackStatusDtosTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsBoundaryScores() {
        BackStatusRequest request = new BackStatusRequest(
            LocalDate.now(),
            new BackRegionStatus(0, 0, 0),
            new BackRegionStatus(10, 10, 10),
            new BackRegionStatus(5, 5, 5),
            "Improving"
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsScoresOutsideRange() {
        BackStatusRequest request = new BackStatusRequest(
            LocalDate.now(),
            new BackRegionStatus(-1, 0, 0),
            new BackRegionStatus(0, 11, 0),
            new BackRegionStatus(0, 0, 0),
            null
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingRegion() {
        BackStatusRequest request = new BackStatusRequest(
            LocalDate.now(),
            null,
            new BackRegionStatus(0, 0, 0),
            new BackRegionStatus(0, 0, 0),
            null
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingScore() {
        BackStatusRequest request = new BackStatusRequest(
            LocalDate.now(),
            new BackRegionStatus(null, 0, 0),
            new BackRegionStatus(0, 0, 0),
            new BackRegionStatus(0, 0, 0),
            null
        );

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsLongNote() {
        BackStatusRequest request = new BackStatusRequest(
            LocalDate.now(),
            new BackRegionStatus(0, 0, 0),
            new BackRegionStatus(0, 0, 0),
            new BackRegionStatus(0, 0, 0),
            "N".repeat(501)
        );

        assertFalse(validator.validate(request).isEmpty());
    }
}
