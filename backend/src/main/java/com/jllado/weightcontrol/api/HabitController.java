package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.HabitDtos.HabitRequest;
import com.jllado.weightcontrol.api.dto.HabitDtos.HabitResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardService;
import com.jllado.weightcontrol.service.HabitService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService service;
    private final CurrentUserService currentUserService;
    private final DashboardService dashboardService;

    public HabitController(HabitService service, CurrentUserService currentUserService, DashboardService dashboardService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public List<HabitResponse> all() {
        return service.findAll(currentUserService.requireUser()).stream().map(HabitResponse::from).toList();
    }

    @PostMapping
    public HabitResponse create(@Valid @RequestBody HabitRequest request) {
        return HabitResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public HabitResponse update(@PathVariable Long id, @Valid @RequestBody HabitRequest request) {
        return HabitResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }

    @PostMapping("/{id}/complete")
    public HabitResponse complete(@PathVariable Long id, @RequestParam LocalDate date) {
        User user = currentUserService.requireUser();
        HabitResponse response = HabitResponse.from(service.complete(user, id, date));
        dashboardService.refreshCurrentStatus(user);
        return response;
    }
}
