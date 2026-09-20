package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.CoachNoteDtos.CoachNoteRequest;
import com.jllado.weightcontrol.domain.CoachNote;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.CoachNoteRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoachNoteServiceTest {
    @Mock private CoachNoteRepository repository;
    @Test void createsTrimmedOwnedNote() {
        User user = user(1L); CoachNoteService service = new CoachNoteService(repository);
        when(repository.save(any())).thenAnswer(invocation -> { CoachNote note = invocation.getArgument(0); note.setId(8L); return note; });
        CoachNote note = service.create(user, new CoachNoteRequest(LocalDate.now().minusDays(1), "  Review sleep  "));
        assertEquals(8L, note.getId()); assertEquals(user, note.getUser()); assertEquals("Review sleep", note.getContent());
    }
    @Test void hidesAnotherUsersNote() {
        User user = user(1L); CoachNoteService service = new CoachNoteService(repository);
        when(repository.findByUserAndId(user, 9L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.delete(user, 9L));
    }
    @Test void rejectsFutureDates() { assertThrows(BadRequestException.class, () -> new CoachNoteService(repository).create(user(1L), new CoachNoteRequest(LocalDate.now().plusDays(1), "Tomorrow"))); }
    private User user(Long id) { User user = new User(); user.setId(id); return user; }
}
