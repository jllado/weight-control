package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineCheckinRequest;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardService;
import com.jllado.weightcontrol.service.RoutineService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routines")
public class RoutineController {

    private final RoutineService service;
    private final CurrentUserService currentUserService;
    private final DashboardService dashboardService;

    public RoutineController(RoutineService service, CurrentUserService currentUserService, DashboardService dashboardService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public List<RoutineResponse> all() {
        return service.findAll(currentUserService.requireUser()).stream()
            .map(routine -> RoutineResponse.from(routine, service.getCheckins(routine)))
            .toList();
    }

    @PostMapping
    public RoutineResponse create(@Valid @RequestBody RoutineRequest request) {
        var routine = service.create(currentUserService.requireUser(), request);
        return RoutineResponse.from(routine, service.getCheckins(routine));
    }

    @PutMapping("/{id}")
    public RoutineResponse update(@PathVariable Long id, @Valid @RequestBody RoutineRequest request) {
        var routine = service.update(currentUserService.requireUser(), id, request);
        return RoutineResponse.from(routine, service.getCheckins(routine));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }

    @PostMapping("/{id}/checkins")
    public RoutineResponse checkin(@PathVariable Long id, @Valid @RequestBody RoutineCheckinRequest request) {
        User user = currentUserService.requireUser();
        var routine = service.checkin(user, id, request.date());
        dashboardService.refreshCurrentStatus(user);
        return RoutineResponse.from(routine, service.getCheckins(routine));
    }

    @DeleteMapping("/{id}/checkins")
    public RoutineResponse undoCheckin(@PathVariable Long id, @Valid @RequestBody RoutineCheckinRequest request) {
        User user = currentUserService.requireUser();
        var routine = service.undoCheckin(user, id, request.date());
        dashboardService.refreshCurrentStatus(user);
        return RoutineResponse.from(routine, service.getCheckins(routine));
    }
}
