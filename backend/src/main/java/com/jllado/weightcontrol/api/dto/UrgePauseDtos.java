package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.UrgePause;
import com.jllado.weightcontrol.domain.DecisionOutcomeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class UrgePauseDtos {
    private UrgePauseDtos() { }
    public record StartRequest(@Size(max = 500) String description) { }
    public record CheckInRequest(@NotNull UrgePause.Answer answer) { }
    public record FinishRequest(DecisionOutcomeType outcome, @Size(max = 500) String reason) { }
    public record CurrentResponse(PauseResponse pause, OffsetDateTime serverNow) { }
    public record PauseResponse(Long id, String description, OffsetDateTime startedAt, OffsetDateTime endsAt,
                                UrgePause.Status status, UrgePause.Answer answer) {
        public static PauseResponse from(UrgePause pause) {
            return new PauseResponse(pause.getId(), pause.getDescription(), pause.getStartedAt(), pause.getEndsAt(), pause.getStatus(), pause.getAnswer());
        }
    }
}
