package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.SicknessSeverity;
import com.jllado.weightcontrol.domain.SicknessType;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;

public final class SicknessDtos {

    private SicknessDtos() {
    }

    public record SicknessRequest(
        @NotNull LocalDate date,
        @NotNull SicknessType type,
        @NotNull SicknessSeverity severity,
        @Size(max = 500) String note
    ) {
    }

    public record CoachSicknessRequest(
        @NotNull LocalDate date,
        @NotNull SicknessType type,
        @NotNull SicknessSeverity severity,
        @Size(max = 500) String note,
        @AssertTrue boolean confirmed
    ) {
        public SicknessRequest sickness() {
            return new SicknessRequest(date, type, severity, note);
        }
    }

    public record SicknessResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        SicknessType type,
        SicknessSeverity severity,
        String note
    ) {
        public static SicknessResponse from(Sickness sickness) {
            return new SicknessResponse(
                sickness.getId(),
                DateTimes.formatDate(sickness.getSicknessDate()),
                sickness.getSicknessDate(),
                sickness.getType(),
                sickness.getSeverity(),
                sickness.getNote()
            );
        }
    }
}
