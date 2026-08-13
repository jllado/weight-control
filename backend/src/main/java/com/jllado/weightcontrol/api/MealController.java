package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.MealService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meals")
public class MealController {

    private final MealService service;
    private final CurrentUserService currentUserService;

    public MealController(MealService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<MealResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(MealResponse::from).toList();
    }

    @PostMapping
    public MealResponse create(@Valid @RequestBody MealRequest request) {
        return MealResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public MealResponse update(@PathVariable Long id, @Valid @RequestBody MealRequest request) {
        return MealResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
