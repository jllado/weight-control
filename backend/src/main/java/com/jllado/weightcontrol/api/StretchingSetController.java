package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.StretchingSetService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stretching-sets")
public class StretchingSetController {
    private final StretchingSetService service;
    private final CurrentUserService currentUser;
    public StretchingSetController(StretchingSetService service, CurrentUserService currentUser) { this.service = service; this.currentUser = currentUser; }
    @GetMapping public List<StretchingSetResponse> all() { return service.findAll(currentUser.requireUser()); }
    @PostMapping public StretchingSetResponse create(@Valid @RequestBody StretchingSetRequest request) { return service.create(currentUser.requireUser(), request); }
    @PutMapping("/{id}") public StretchingSetResponse update(@PathVariable Long id, @Valid @RequestBody StretchingSetRequest request) { return service.update(currentUser.requireUser(), id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(currentUser.requireUser(), id); }
    @ExceptionHandler(DataIntegrityViolationException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public String conflict() { return "The stretching set could not be saved. Check that its name is unique."; }
}
