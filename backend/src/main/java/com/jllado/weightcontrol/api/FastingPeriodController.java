package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodRequest;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.FastingPeriodService;
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
@RequestMapping("/api/fasting-periods")
public class FastingPeriodController {

    private final FastingPeriodService service;
    private final CurrentUserService currentUserService;

    public FastingPeriodController(FastingPeriodService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<FastingPeriodResponse> all() {
        return service.findAll(currentUserService.requireUser()).stream().map(FastingPeriodResponse::from).toList();
    }

    @PostMapping
    public FastingPeriodResponse create(@Valid @RequestBody FastingPeriodRequest request) {
        return FastingPeriodResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public FastingPeriodResponse update(@PathVariable Long id, @Valid @RequestBody FastingPeriodRequest request) {
        return FastingPeriodResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
