package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutRequest;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutResponse;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.DashboardWorkoutResponse;
import com.jllado.weightcontrol.api.dto.WorkoutDtos.WorkoutDiaryPageResponse;
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
import org.springframework.data.domain.Page;
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
        workouts.currentWorkouts().forEach(workout -> workoutIds.add(workout.getId()));
        workouts.previousWeekWorkouts().forEach(workout -> workoutIds.add(workout.getId()));
        return new DashboardWorkoutResponse(
            workouts.currentWorkouts().stream().map(WorkoutResponse::from).toList(),
            workouts.previousWeekWorkouts().stream().map(WorkoutResponse::from).toList(),
            workouts.preloadWorkouts().stream().map(WorkoutResponse::from).toList(),
            personalRecordService.workoutHistory(user, workoutIds),
            service.days(user, java.util.stream.Stream.concat(workouts.currentWorkouts().stream(), workouts.previousWeekWorkouts().stream()).toList())
        );
    }

    @GetMapping("/diary")
    public WorkoutDiaryPageResponse diary(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        User user = currentUserService.requireUser();
        Page<LocalDate> dates = service.findDiaryPage(user, page, size);
        var workouts = service.findOnDates(user, dates.getContent());
        var ids = workouts.stream().map(com.jllado.weightcontrol.domain.Workout::getId).collect(java.util.stream.Collectors.toSet());
        return new WorkoutDiaryPageResponse(
            service.days(user, workouts),
            personalRecordService.workoutHistory(user, ids),
            dates.getNumber(), dates.getSize(), dates.getTotalElements(), dates.getTotalPages()
        );
    }

    @GetMapping("/preload")
    public List<WorkoutResponse> preload(@RequestParam LocalDate through) {
        return service.findPreloadWorkouts(currentUserService.requireUser(), through).stream().map(WorkoutResponse::from).toList();
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
