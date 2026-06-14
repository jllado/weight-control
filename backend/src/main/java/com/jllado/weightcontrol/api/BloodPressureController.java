package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.BloodPressureDtos.BloodPressureRequest;
import com.jllado.weightcontrol.api.dto.BloodPressureDtos.BloodPressureResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.BloodPressureService;
import com.jllado.weightcontrol.service.DashboardService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blood-pressures")
public class BloodPressureController {

    private final BloodPressureService service;
    private final CurrentUserService currentUserService;
    private final DashboardService dashboardService;

    public BloodPressureController(BloodPressureService service, CurrentUserService currentUserService, DashboardService dashboardService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public List<BloodPressureResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(BloodPressureResponse::from).toList();
    }

    @PostMapping
    public BloodPressureResponse create(@Valid @RequestBody BloodPressureRequest request) {
        User user = currentUserService.requireUser();
        var bloodPressure = service.create(user, request);
        dashboardService.refreshCurrentStatus(user);
        return BloodPressureResponse.from(bloodPressure);
    }

    @PutMapping("/{id}")
    public BloodPressureResponse update(@PathVariable Long id, @Valid @RequestBody BloodPressureRequest request) {
        User user = currentUserService.requireUser();
        var bloodPressure = service.update(user, id, request);
        dashboardService.refreshCurrentStatus(user);
        return BloodPressureResponse.from(bloodPressure);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        User user = currentUserService.requireUser();
        service.delete(user, id);
        dashboardService.refreshCurrentStatus(user);
    }
}
