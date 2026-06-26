package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CalorieDtos.CalorieRequest;
import com.jllado.weightcontrol.api.dto.CalorieDtos.CalorieResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CalorieService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/calories")
public class CalorieController {

    private final CalorieService service;
    private final CurrentUserService currentUserService;

    public CalorieController(CalorieService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<CalorieResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(CalorieResponse::from).toList();
    }

    @PostMapping
    public CalorieResponse create(@Valid @RequestBody CalorieRequest request) {
        return CalorieResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public CalorieResponse update(@PathVariable Long id, @Valid @RequestBody CalorieRequest request) {
        return CalorieResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
