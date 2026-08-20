package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.HealthConstraintRequest;
import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.HealthConstraintResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.HealthConstraintService;
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
@RequestMapping("/api/health-constraints")
public class HealthConstraintController {

    private final HealthConstraintService service;
    private final CurrentUserService currentUserService;

    public HealthConstraintController(HealthConstraintService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<HealthConstraintResponse> all() {
        return service.findAll(currentUserService.requireUser()).stream().map(HealthConstraintResponse::from).toList();
    }

    @PostMapping
    public HealthConstraintResponse create(@Valid @RequestBody HealthConstraintRequest request) {
        return HealthConstraintResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public HealthConstraintResponse update(
        @PathVariable Long id,
        @Valid @RequestBody HealthConstraintRequest request
    ) {
        return HealthConstraintResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
