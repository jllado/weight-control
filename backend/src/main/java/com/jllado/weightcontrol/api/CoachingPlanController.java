package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachingPlanRequest;
import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachingPlanResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CoachingPlanService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coaching-plan")
public class CoachingPlanController {

    private final CoachingPlanService service;
    private final CurrentUserService currentUserService;

    public CoachingPlanController(CoachingPlanService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<CoachingPlanResponse> get() {
        return service.find(currentUserService.requireUser())
            .map(CoachingPlanResponse::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public CoachingPlanResponse replace(@Valid @RequestBody CoachingPlanRequest request) {
        return CoachingPlanResponse.from(service.replace(currentUserService.requireUser(), request));
    }
}
