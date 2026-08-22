package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.HabitDtos.HabitRequest;
import com.jllado.weightcontrol.api.dto.HabitDtos.HabitResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardService;
import com.jllado.weightcontrol.service.HabitService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
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
    private final PersonalRecordMutationService mutationService;

    public HabitController(HabitService service, CurrentUserService currentUserService, DashboardService dashboardService, PersonalRecordMutationService mutationService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.dashboardService = dashboardService;
        this.mutationService = mutationService;
    }

    @GetMapping
    public List<HabitResponse> all() {
        return service.findAll(currentUserService.requireUser()).stream().map(habit -> HabitResponse.from(habit, service.getCheckins(habit), service.getBaseline(habit))).toList();
    }

    @PostMapping
    public HabitResponse create(@Valid @RequestBody HabitRequest request) {
        var habit = service.create(currentUserService.requireUser(), request);
        return HabitResponse.from(habit, service.getCheckins(habit), service.getBaseline(habit));
    }

    @PutMapping("/{id}")
    public HabitResponse update(@PathVariable Long id, @Valid @RequestBody HabitRequest request) {
        var habit = mutationService.updateHabit(currentUserService.requireUser(), id, request);
        return HabitResponse.from(habit, service.getCheckins(habit), service.getBaseline(habit));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mutationService.deleteHabit(currentUserService.requireUser(), id);
    }

    @PostMapping("/{id}/complete")
    public RecordMutationResponse<HabitResponse> complete(@PathVariable Long id, @RequestParam LocalDate date) {
        User user = currentUserService.requireUser();
        var result = mutationService.completeHabit(user, id, date);
        dashboardService.refreshCurrentStatus(user);
        return new RecordMutationResponse<>(HabitResponse.from(result.result(), service.getCheckins(result.result()), service.getBaseline(result.result())), result.achievements());
    }

    @DeleteMapping("/{id}/checkins")
    public HabitResponse undoCompletion(@PathVariable Long id, @RequestParam LocalDate date) {
        User user = currentUserService.requireUser();
        var habit = mutationService.undoHabitCompletion(user, id, date);
        dashboardService.refreshCurrentStatus(user);
        return HabitResponse.from(habit, service.getCheckins(habit), service.getBaseline(habit));
    }
}
