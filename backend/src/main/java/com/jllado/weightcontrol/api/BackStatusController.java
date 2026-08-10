package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.BackStatusDtos.BackStatusRequest;
import com.jllado.weightcontrol.api.dto.BackStatusDtos.BackStatusResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.BackStatusService;
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
@RequestMapping("/api/back-statuses")
public class BackStatusController {

    private final BackStatusService service;
    private final CurrentUserService currentUserService;

    public BackStatusController(BackStatusService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<BackStatusResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(BackStatusResponse::from).toList();
    }

    @PostMapping
    public BackStatusResponse create(@Valid @RequestBody BackStatusRequest request) {
        return BackStatusResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public BackStatusResponse update(@PathVariable Long id, @Valid @RequestBody BackStatusRequest request) {
        return BackStatusResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
