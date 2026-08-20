package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CoachDtos.CoachCatalogResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachContextResponse;
import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.CoachHealthConstraintRequest;
import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.HealthConstraintResponse;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.HealthDataContextService;
import com.jllado.weightcontrol.service.HealthConstraintService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatgpt-actions/coach")
public class ChatGptCoachActionController {

    private final HealthDataContextService healthDataContextService;
    private final HealthConstraintService healthConstraintService;
    private final CurrentUserService currentUserService;

    public ChatGptCoachActionController(
        HealthDataContextService healthDataContextService,
        HealthConstraintService healthConstraintService,
        CurrentUserService currentUserService
    ) {
        this.healthDataContextService = healthDataContextService;
        this.healthConstraintService = healthConstraintService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/catalog")
    public CoachCatalogResponse getCoachCatalog() {
        return healthDataContextService.getCoachCatalog(currentUserService.requireUser());
    }

    @GetMapping("/context")
    public CoachContextResponse getHealthContext(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam Set<CoachDomain> domains
    ) {
        return healthDataContextService.getHealthContext(currentUserService.requireUser(), from, to, domains);
    }

    @GetMapping("/health-constraints")
    public List<HealthConstraintResponse> getHealthConstraints() {
        return healthConstraintService.findAll(currentUserService.requireUser()).stream()
            .map(HealthConstraintResponse::from)
            .toList();
    }

    @PostMapping("/health-constraints")
    public HealthConstraintResponse createHealthConstraint(
        @Valid @RequestBody CoachHealthConstraintRequest request
    ) {
        return HealthConstraintResponse.from(
            healthConstraintService.createConfirmed(currentUserService.requireUser(), request)
        );
    }

    @PutMapping("/health-constraints/{id}")
    public HealthConstraintResponse updateHealthConstraint(
        @PathVariable Long id,
        @Valid @RequestBody CoachHealthConstraintRequest request
    ) {
        return HealthConstraintResponse.from(
            healthConstraintService.updateConfirmed(currentUserService.requireUser(), id, request)
        );
    }
}
