package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.InAppNotificationDtos.PendingNotificationResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.InAppNotificationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class InAppNotificationController {

    private final InAppNotificationService service;
    private final CurrentUserService currentUserService;

    public InAppNotificationController(InAppNotificationService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/pending")
    public List<PendingNotificationResponse> pending() {
        User user = currentUserService.requireUser();
        return service.findPending(user).stream().map(PendingNotificationResponse::from).toList();
    }

    @PostMapping("/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable Long id) {
        service.dismiss(currentUserService.requireUser(), id);
    }

    @PostMapping("/dismiss-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismissAll() {
        service.dismissAll(currentUserService.requireUser());
    }
}
