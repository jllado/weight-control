package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DecisionOutcomeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decision-outcomes")
public class DecisionOutcomeController {

    private final DecisionOutcomeService service;
    private final CurrentUserService currentUserService;

    public DecisionOutcomeController(DecisionOutcomeService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public DecisionOutcomeResponse create(@Valid @RequestBody DecisionOutcomeRequest request) {
        return DecisionOutcomeResponse.from(service.create(currentUserService.requireUser(), request));
    }
}
