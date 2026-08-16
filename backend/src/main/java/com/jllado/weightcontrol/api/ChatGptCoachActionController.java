package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CoachDtos.CoachCatalogResponse;
import com.jllado.weightcontrol.api.dto.CoachDtos.CoachContextResponse;
import com.jllado.weightcontrol.domain.CoachDomain;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.HealthDataContextService;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatgpt-actions/coach")
public class ChatGptCoachActionController {

    private final HealthDataContextService healthDataContextService;
    private final CurrentUserService currentUserService;

    public ChatGptCoachActionController(
        HealthDataContextService healthDataContextService,
        CurrentUserService currentUserService
    ) {
        this.healthDataContextService = healthDataContextService;
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
}
