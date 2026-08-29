package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.LipidPanel;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;

public final class LipidPanelDtos {

    private LipidPanelDtos() {
    }

    public record LipidPanelRequest(
        @NotNull LocalDate date,
        @NotNull @Positive Integer totalCholesterol,
        @NotNull @Positive Integer hdlCholesterol,
        @NotNull @Positive Integer ldlCholesterol,
        @NotNull @Positive Integer triglycerides
    ) {
    }

    public record CoachLipidPanelRequest(
        @NotNull LocalDate date,
        @NotNull @Positive Integer totalCholesterol,
        @NotNull @Positive Integer hdlCholesterol,
        @NotNull @Positive Integer ldlCholesterol,
        @NotNull @Positive Integer triglycerides,
        @AssertTrue boolean confirmed
    ) {
        public LipidPanelRequest lipidPanel() {
            return new LipidPanelRequest(date, totalCholesterol, hdlCholesterol, ldlCholesterol, triglycerides);
        }
    }

    public record LipidPanelResponse(
        Long id,
        String dateFormat,
        LocalDate date,
        Integer totalCholesterol,
        Integer hdlCholesterol,
        Integer ldlCholesterol,
        Integer triglycerides
    ) {
        public static LipidPanelResponse from(LipidPanel panel) {
            return new LipidPanelResponse(
                panel.getId(),
                DateTimes.formatDate(panel.getPanelDate()),
                panel.getPanelDate(),
                panel.getTotalCholesterol(),
                panel.getHdlCholesterol(),
                panel.getLdlCholesterol(),
                panel.getTriglycerides()
            );
        }
    }
}
