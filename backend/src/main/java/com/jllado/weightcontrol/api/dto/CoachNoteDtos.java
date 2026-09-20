package com.jllado.weightcontrol.api.dto;

import com.jllado.weightcontrol.domain.CoachNote;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class CoachNoteDtos {
    private CoachNoteDtos() { }
    public record CoachNoteRequest(@NotNull LocalDate date, @NotBlank @Size(max = 4000) String content) { }
    public record CoachNoteActionRequest(@NotNull LocalDate date, @NotBlank @Size(max = 4000) String content, @AssertTrue boolean confirmed) {
        public CoachNoteRequest note() { return new CoachNoteRequest(date, content); }
    }
    public record CoachNoteResponse(Long id, LocalDate date, String content) {
        public static CoachNoteResponse from(CoachNote note) { return new CoachNoteResponse(note.getId(), note.getNoteDate(), note.getContent()); }
    }
}
