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
    public WarningReadResponse get(@RequestParam(defaultValue = "ACTIVE") WarningView view,
                                   @RequestParam(required = false) Long id,
                                   @RequestParam(defaultValue = "0") int page) {
        return service.read(currentUser.requireUser(), view, id, page);
    }

    @PostMapping
    public WarningResponse save(@Valid @RequestBody WarningWriteRequest request) {
        return service.write(currentUser.requireUser(), request);
    }
}
