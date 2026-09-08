package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CoachDtos.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CoachWarningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatgpt-actions/coach/warnings")
public class ChatGptCoachWarningActionController {
    private final CoachWarningService service;
    private final CurrentUserService currentUser;

    public ChatGptCoachWarningActionController(CoachWarningService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public WarningOverview get() { return service.overview(currentUser.requireUser()); }

    @GetMapping("/history")
    public WarningPage history(@RequestParam(defaultValue = "0") int page) { return service.history(currentUser.requireUser(), page); }

    @GetMapping("/{id}/revisions")
    public WarningPage revisions(@PathVariable Long id, @RequestParam(defaultValue = "0") int page) { return service.revisions(currentUser.requireUser(), id, page); }

    @PostMapping
    public WarningResponse create(@Valid @RequestBody CreateWarningRequest request) { return service.create(currentUser.requireUser(), request); }

    @PutMapping("/{id}")
    public WarningResponse update(@PathVariable Long id, @Valid @RequestBody UpdateWarningRequest request) { return service.update(currentUser.requireUser(), id, request); }

    @PostMapping("/{id}/resolve")
    public WarningResponse resolve(@PathVariable Long id, @Valid @RequestBody ResolveWarningRequest request) { return service.resolve(currentUser.requireUser(), id, request); }
}
