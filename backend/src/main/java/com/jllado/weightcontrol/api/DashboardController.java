package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.DashboardDtos.DashboardResponse;
import com.jllado.weightcontrol.api.dto.DashboardDtos.DashboardCompletionRequest;
import com.jllado.weightcontrol.api.dto.DashboardCoachMetricsDtos.DashboardCoachMetricsResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardCoachMetricsService;
import com.jllado.weightcontrol.service.DashboardService;
import com.jllado.weightcontrol.service.PersonalRecordService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserService currentUserService;
    private final PersonalRecordService personalRecordService;
    private final DashboardCoachMetricsService dashboardCoachMetricsService;

    public DashboardController(DashboardService dashboardService, CurrentUserService currentUserService, PersonalRecordService personalRecordService, DashboardCoachMetricsService dashboardCoachMetricsService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;
        this.personalRecordService = personalRecordService;
        this.dashboardCoachMetricsService = dashboardCoachMetricsService;
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
        var user = currentUserService.requireUser();
        DashboardResponse response = dashboardService.refresh(user);
        personalRecordService.rebuild(user);
        return response;
    }

    @PostMapping("/completion")
    public DashboardResponse setDashboardCompletion(@Valid @RequestBody DashboardCompletionRequest request) {
        var user = currentUserService.requireUser();
        DashboardResponse response = dashboardService.setDashboardCompletion(user, request.completed());
        personalRecordService.rebuild(user);
        return response;
    }

    @GetMapping("/coach-metrics")
    public DashboardCoachMetricsResponse coachMetrics(
        @RequestParam LocalDate selectedDate,
        @RequestParam(defaultValue = "MONTHLY") DashboardCoachMetricsService.ChartPeriod period
    ) {
        return dashboardCoachMetricsService.get(currentUserService.requireUser(), selectedDate, period);
    }
}
