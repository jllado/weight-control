package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.ReflectionDtos.PagedReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DashboardReflectionService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public PagedReflectionOverviewResponse getOverview(@RequestParam(defaultValue = "0") int page) {
        return reflectionService.getPagedOverview(currentUserService.requireUser(), page);
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
