package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CoachNoteDtos.CoachNoteRequest;
import com.jllado.weightcontrol.api.dto.CoachNoteDtos.CoachNoteResponse;
import com.jllado.weightcontrol.api.dto.CommonDtos.DeletionResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CoachNoteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coach-notes")
public class CoachNoteController {
    private final CoachNoteService service; private final CurrentUserService currentUser;
    public CoachNoteController(CoachNoteService service, CurrentUserService currentUser) { this.service = service; this.currentUser = currentUser; }
    @GetMapping public List<CoachNoteResponse> list() { return service.findAll(currentUser.requireUser()).stream().map(CoachNoteResponse::from).toList(); }
    @PostMapping public CoachNoteResponse create(@Valid @RequestBody CoachNoteRequest request) { return CoachNoteResponse.from(service.create(currentUser.requireUser(), request)); }
    @PutMapping("/{id}") public CoachNoteResponse update(@PathVariable Long id, @Valid @RequestBody CoachNoteRequest request) { return CoachNoteResponse.from(service.update(currentUser.requireUser(), id, request)); }
    @DeleteMapping("/{id}") public DeletionResponse delete(@PathVariable Long id) { service.delete(currentUser.requireUser(), id); return new DeletionResponse(true); }
}
