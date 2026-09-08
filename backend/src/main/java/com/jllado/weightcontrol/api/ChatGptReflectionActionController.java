package com.jllado.weightcontrol.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionOverviewResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.ReflectionResponse;
import com.jllado.weightcontrol.api.dto.ReflectionDtos.SaveReflectionRequest;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.GptActionNotificationService;
import com.jllado.weightcontrol.service.DashboardReflectionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatgpt-actions/reflections")
public class ChatGptReflectionActionController {

    private final DashboardReflectionService reflectionService;
    private final CurrentUserService currentUserService;
    private final GptActionNotificationService actionNotifications;

    public ChatGptReflectionActionController(
        DashboardReflectionService reflectionService,
        CurrentUserService currentUserService,
        GptActionNotificationService actionNotifications
    ) {
        this.reflectionService = reflectionService;
        this.currentUserService = currentUserService;
        this.actionNotifications = actionNotifications;
    }

    @GetMapping("/overview")
    public ReflectionOverviewResponse getOverview() {
        return reflectionService.getOverview(currentUserService.requireUser());
    }

    @GetMapping("/{date}/context")
    public JsonNode getContext(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return reflectionService.getContext(currentUserService.requireUser(), date);
    }

    @PostMapping("/{date}")
    public ReflectionResponse saveReflection(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Valid @RequestBody SaveReflectionRequest request
    ) {
        return actionNotifications.execute(currentUserService.requireUser(), "Reflection saved", "/reflections",
            () -> ReflectionResponse.from(reflectionService.save(currentUserService.requireUser(), date, request))
        );
    }
}
