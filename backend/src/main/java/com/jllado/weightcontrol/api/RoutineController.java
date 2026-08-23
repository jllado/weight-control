package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineCheckinRequest;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineReminderSnoozeRequest;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineReminderSnoozeResponse;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineResponse;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineCheckinMutationResponse;
import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineSummaryResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardService;
import com.jllado.weightcontrol.service.RoutineService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routines")
public class RoutineController {

    private final RoutineService service;
    private final CurrentUserService currentUserService;
    private final DashboardService dashboardService;
    private final PersonalRecordMutationService mutationService;

    public RoutineController(RoutineService service, CurrentUserService currentUserService, DashboardService dashboardService, PersonalRecordMutationService mutationService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.dashboardService = dashboardService;
        this.mutationService = mutationService;
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
        var routine = mutationService.updateRoutine(currentUserService.requireUser(), id, request);
        return RoutineResponse.from(routine, service.getCheckins(routine));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mutationService.deleteRoutine(currentUserService.requireUser(), id);
    }

    @PostMapping("/{id}/checkins")
    public RecordMutationResponse<RoutineCheckinMutationResponse> checkin(@PathVariable Long id, @Valid @RequestBody RoutineCheckinRequest request) {
        User user = currentUserService.requireUser();
        var result = mutationService.checkinRoutine(user, id, request.date());
        mutationService.refreshRoutineDashboard(user, request.date());
        return new RecordMutationResponse<>(
            new RoutineCheckinMutationResponse(
                RoutineSummaryResponse.from(result.routine()),
                request.date(),
                result.checkin() != null,
                dashboardService.getDashboard(user)
            ),
            result.achievements()
        );
    }

    @DeleteMapping("/{id}/checkins")
    public RoutineCheckinMutationResponse undoCheckin(@PathVariable Long id, @Valid @RequestBody RoutineCheckinRequest request) {
        User user = currentUserService.requireUser();
        var routine = mutationService.undoRoutineCheckin(user, id, request.date());
        mutationService.refreshRoutineDashboard(user, request.date());
        return new RoutineCheckinMutationResponse(
            RoutineSummaryResponse.from(routine),
            request.date(),
            true,
            dashboardService.getDashboard(user)
        );
    }

    @PostMapping("/{id}/reminders/{reminderId}/snooze")
    public RoutineReminderSnoozeResponse snoozeReminder(
        @PathVariable Long id,
        @PathVariable Long reminderId,
        @Valid @RequestBody RoutineReminderSnoozeRequest request
    ) {
        var nextReminderAt = service.snoozeReminder(currentUserService.requireUser(), id, reminderId, request.minutes());
        return new RoutineReminderSnoozeResponse(nextReminderAt);
    }
}
