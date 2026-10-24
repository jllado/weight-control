package com.jllado.weightcontrol.api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class ActionDtos {
    private ActionDtos() { }
    public record ActionRequest(@NotBlank @Size(max = 255) String wording, @NotNull LocalDate startDate,
        @Min(1) int targetDays, @Size(max = 120) String sourceKey) { }
    public record ActionResponse(Long id, String wording, LocalDate startDate, int targetDays, boolean goalLocked,
        LocalDate stoppedDate, Long restartedFromId, String sourceKey, String sourceLabel, String status,
        int streak, int bestStreak, int percentage, LocalDate completedDate, boolean checked,
        List<LocalDate> checkinDates) { }
    public record SuggestionResponse(String sourceKey, String wording, String sourceLabel, List<String> provenance,
        Long existingAttemptId) { }
}
