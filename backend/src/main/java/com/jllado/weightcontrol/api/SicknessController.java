package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.SicknessDtos.SicknessRequest;
import com.jllado.weightcontrol.api.dto.SicknessDtos.SicknessResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.SicknessService;
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
@RequestMapping("/api/sicknesses")
public class SicknessController {

    private final SicknessService service;
    private final CurrentUserService currentUserService;

    public SicknessController(SicknessService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<SicknessResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(SicknessResponse::from).toList();
    }

    @PostMapping
    public SicknessResponse create(@Valid @RequestBody SicknessRequest request) {
        return SicknessResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public SicknessResponse update(@PathVariable Long id, @Valid @RequestBody SicknessRequest request) {
        return SicknessResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
