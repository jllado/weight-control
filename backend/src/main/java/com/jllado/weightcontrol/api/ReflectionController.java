package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardReflectionService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reflections")
public class ReflectionController {

    private final DashboardReflectionService reflectionService;
    private final CurrentUserService currentUserService;

    public ReflectionController(DashboardReflectionService reflectionService, CurrentUserService currentUserService) {
        this.reflectionService = reflectionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ReflectionOverviewResponse getOverview() {
        return reflectionService.getOverview(currentUserService.requireUser());
    }

    @GetMapping("/{date}")
    public ResponseEntity<ReflectionResponse> getReflection(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reflectionService.find(currentUserService.requireUser(), date)
            .map(ReflectionResponse::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
