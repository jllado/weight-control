package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.DashboardDtos.DashboardResponse;
import com.jllado.weightcontrol.api.dto.DashboardDtos.DashboardCompletionRequest;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserService currentUserService;

    public DashboardController(DashboardService dashboardService, CurrentUserService currentUserService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public DashboardResponse getDashboard() {
        return dashboardService.getDashboard(currentUserService.requireUser());
    }

    @PostMapping("/advance")
    public DashboardResponse advance() {
        return dashboardService.advance(currentUserService.requireUser());
    }

    @PostMapping("/retreat")
    public DashboardResponse retreat() {
        return dashboardService.retreat(currentUserService.requireUser());
    }

    @PostMapping("/refresh")
    public DashboardResponse refresh() {
        return dashboardService.refresh(currentUserService.requireUser());
    }

    @PostMapping("/completion")
    public DashboardResponse setDashboardCompletion(@Valid @RequestBody DashboardCompletionRequest request) {
        return dashboardService.setDashboardCompletion(currentUserService.requireUser(), request.completed());
    }
}
