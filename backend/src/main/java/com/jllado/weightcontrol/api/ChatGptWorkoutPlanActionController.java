package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WorkoutDtos.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.GptActionNotificationService;
import com.jllado.weightcontrol.service.WorkoutPlanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatgpt-actions/coach/active-plan")
public class ChatGptWorkoutPlanActionController {
    private final WorkoutPlanService service;
    private final CurrentUserService currentUser;
    private final GptActionNotificationService notifications;
    public ChatGptWorkoutPlanActionController(WorkoutPlanService service, CurrentUserService currentUser, GptActionNotificationService notifications) {
        this.service = service; this.currentUser = currentUser; this.notifications = notifications;
    }
    @GetMapping(params = "target=WORKOUT") public WorkoutPlanEditContext context() { return service.editContext(currentUser.requireUser()); }
    @PutMapping(params = "target=WORKOUT") public WorkoutPlanResponse update(@Valid @RequestBody CoachWorkoutPlanUpdateRequest request) {
        var user = currentUser.requireUser();
        return notifications.execute(user, "Workout plan updated", "/workouts?tab=plan", () -> service.updateConfirmed(user, request));
    }
}
