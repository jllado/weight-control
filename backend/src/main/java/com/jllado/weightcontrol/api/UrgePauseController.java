package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.UrgePauseDtos.*;
import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.UrgePauseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/urge-pauses")
public class UrgePauseController {
    private final CurrentUserService users;
    private final UrgePauseService service;
    public UrgePauseController(CurrentUserService users, UrgePauseService service) { this.users = users; this.service = service; }
    @GetMapping public CurrentResponse current() { return service.current(users.requireUser()); }
    @PostMapping public CurrentResponse start(@Valid @RequestBody StartRequest request) { return service.start(users.requireUser(), request); }
    @PostMapping("/{id}/check-in") public CurrentResponse checkIn(@PathVariable Long id, @Valid @RequestBody CheckInRequest request) { return service.checkIn(users.requireUser(), id, request); }
    @PostMapping("/{id}/cancel") public CurrentResponse cancel(@PathVariable Long id) { return service.cancel(users.requireUser(), id); }
    @PostMapping("/{id}/repeat") public CurrentResponse repeat(@PathVariable Long id) { return service.repeat(users.requireUser(), id); }
    @PostMapping("/{id}/finish") public RecordMutationResponse<DecisionOutcomeResponse> finish(@PathVariable Long id, @Valid @RequestBody FinishRequest request) { return service.finish(users.requireUser(), id, request); }
}
