package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.api.dto.SleepDtos.SleepResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.SleepService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sleeps")
public class SleepController {

    private final SleepService sleepService;
    private final CurrentUserService currentUserService;

    public SleepController(SleepService sleepService, CurrentUserService currentUserService) {
        this.sleepService = sleepService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<SleepResponse> all() {
        User user = currentUserService.requireUser();
        return sleepService.findAll(user).stream().map(SleepResponse::from).toList();
    }

    @PostMapping
    public SleepResponse create(@Valid @RequestBody SleepRequest request) {
        return SleepResponse.from(sleepService.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public SleepResponse update(@PathVariable Long id, @Valid @RequestBody SleepRequest request) {
        return SleepResponse.from(sleepService.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        sleepService.delete(currentUserService.requireUser(), id);
    }
}
