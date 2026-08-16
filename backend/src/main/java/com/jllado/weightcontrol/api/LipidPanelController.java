package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.LipidPanelDtos.LipidPanelRequest;
import com.jllado.weightcontrol.api.dto.LipidPanelDtos.LipidPanelResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.LipidPanelService;
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
@RequestMapping("/api/lipid-panels")
public class LipidPanelController {

    private final LipidPanelService service;
    private final CurrentUserService currentUserService;

    public LipidPanelController(LipidPanelService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<LipidPanelResponse> all() {
        return service.findAll(currentUserService.requireUser()).stream().map(LipidPanelResponse::from).toList();
    }

    @PostMapping
    public LipidPanelResponse create(@Valid @RequestBody LipidPanelRequest request) {
        return LipidPanelResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public LipidPanelResponse update(@PathVariable Long id, @Valid @RequestBody LipidPanelRequest request) {
        return LipidPanelResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
