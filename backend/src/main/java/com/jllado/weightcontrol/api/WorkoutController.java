package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutResponse;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.DashboardWorkoutResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WorkoutService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.service.PersonalRecordService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService service;
    private final CurrentUserService currentUserService;
    private final PersonalRecordMutationService mutationService;
    private final PersonalRecordService personalRecordService;

    public WorkoutController(WorkoutService service, CurrentUserService currentUserService, PersonalRecordMutationService mutationService, PersonalRecordService personalRecordService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.mutationService = mutationService;
        this.personalRecordService = personalRecordService;
    }

    @GetMapping
    public List<WorkoutResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(WorkoutResponse::from).toList();
    }

    @GetMapping("/dashboard")
    public DashboardWorkoutResponse dashboard(@RequestParam LocalDate date) {
        User user = currentUserService.requireUser();
        var workouts = service.findDashboardWorkouts(user, date);
        var workoutIds = new LinkedHashSet<Long>();
        if (workouts.currentWorkout() != null) {
            workoutIds.add(workouts.currentWorkout().getId());
        }
        if (workouts.previousWeekWorkout() != null) {
            workoutIds.add(workouts.previousWeekWorkout().getId());
        }
        return new DashboardWorkoutResponse(
            workouts.currentWorkout() == null ? null : WorkoutResponse.from(workouts.currentWorkout()),
            workouts.previousWeekWorkout() == null ? null : WorkoutResponse.from(workouts.previousWeekWorkout()),
            workouts.preloadWorkouts().stream().map(WorkoutResponse::from).toList(),
            personalRecordService.workoutHistory(user, workoutIds)
        );
    }

    @PostMapping
    public RecordMutationResponse<WorkoutResponse> create(@Valid @RequestBody WorkoutRequest request) {
        var mutation = mutationService.createWorkout(currentUserService.requireUser(), request);
        return new RecordMutationResponse<>(WorkoutResponse.from(mutation.result()), mutation.achievements());
    }

    @PutMapping("/{id}")
    public RecordMutationResponse<WorkoutResponse> update(@PathVariable Long id, @Valid @RequestBody WorkoutRequest request) {
        var mutation = mutationService.updateWorkout(currentUserService.requireUser(), id, request);
        return new RecordMutationResponse<>(WorkoutResponse.from(mutation.result()), mutation.achievements());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mutationService.deleteWorkout(currentUserService.requireUser(), id);
    }
}
