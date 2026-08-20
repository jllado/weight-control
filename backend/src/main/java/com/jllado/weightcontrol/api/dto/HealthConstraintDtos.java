package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.HealthConstraintSource;
import com.jllado.weightcontrol.domain.HealthConstraintType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class HealthConstraintDtos {

    private HealthConstraintDtos() {
    }

    public record HealthConstraintRequest(
        @NotNull HealthConstraintType type,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String details,
        @NotNull HealthConstraintSource source,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull Boolean active
    ) {
    }

    public record CoachHealthConstraintRequest(
        @NotNull HealthConstraintType type,
        @NotBlank @Size(max = 255) String title,
        @NotBlank String details,
        @NotNull HealthConstraintSource source,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull Boolean active,
        @AssertTrue boolean confirmed
    ) {
        public HealthConstraintRequest constraint() {
            return new HealthConstraintRequest(type, title, details, source, startDate, endDate, active);
        }
    }

    public record HealthConstraintResponse(
        Long id,
        HealthConstraintType type,
        String title,
        String details,
        HealthConstraintSource source,
        LocalDate startDate,
        LocalDate endDate,
        boolean active
    ) {
        public static HealthConstraintResponse from(HealthConstraint constraint) {
            return new HealthConstraintResponse(
                constraint.getId(),
                constraint.getType(),
                constraint.getTitle(),
                constraint.getDetails(),
                constraint.getSource(),
                constraint.getStartDate(),
                constraint.getEndDate(),
                constraint.isActive()
            );
        }
    }
}
