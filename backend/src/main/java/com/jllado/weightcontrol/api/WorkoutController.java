package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WorkoutService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService service;
    private final CurrentUserService currentUserService;

    public WorkoutController(WorkoutService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<WorkoutResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(WorkoutResponse::from).toList();
    }

    @PostMapping
    public WorkoutResponse create(@Valid @RequestBody WorkoutRequest request) {
        return WorkoutResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public WorkoutResponse update(@PathVariable Long id, @Valid @RequestBody WorkoutRequest request) {
        return WorkoutResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
