package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WorkoutPlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workout-plans")
public class WorkoutPlanController {
    private final WorkoutPlanService service;
    private final CurrentUserService currentUser;
    public WorkoutPlanController(WorkoutPlanService service, CurrentUserService currentUser) { this.service = service; this.currentUser = currentUser; }
    @GetMapping("/current") public ResponseEntity<WorkoutPlanResponse> current() { return service.current(currentUser.requireUser()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build()); }
    @GetMapping public WorkoutPlanArchiveResponse archive(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) { return service.archive(currentUser.requireUser(), page, size); }
    @GetMapping("/{id}") public WorkoutPlanResponse get(@PathVariable Long id) { return service.get(currentUser.requireUser(), id); }
    @PostMapping public WorkoutPlanResponse create(@Valid @RequestBody WorkoutPlanRequest request) { return service.create(currentUser.requireUser(), request); }
    @PutMapping("/{id}") public WorkoutPlanResponse update(@PathVariable Long id, @Valid @RequestBody WorkoutPlanUpdateRequest request) { return service.update(currentUser.requireUser(), id, request); }
}
