package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CoachDtos.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CoachWarningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coach-warnings")
public class CoachWarningController {
    private final CoachWarningService service;
    private final CurrentUserService currentUser;

    public CoachWarningController(CoachWarningService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public WarningOverview get() { return service.overview(currentUser.requireUser()); }

    @GetMapping("/history")
    public WarningPage history(@RequestParam(defaultValue = "0") int page) { return service.history(currentUser.requireUser(), page); }

    @GetMapping("/{id}/revisions")
    public WarningPage revisions(@PathVariable Long id, @RequestParam(defaultValue = "0") int page) { return service.revisions(currentUser.requireUser(), id, page); }
}
