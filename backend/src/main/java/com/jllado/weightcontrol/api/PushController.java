package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.PushDtos.PushConfigResponse;
import com.jllado.weightcontrol.api.dto.PushDtos.AgendaResponse;
import com.jllado.weightcontrol.api.dto.PushDtos.PushEndpointRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.ReleaseNotificationRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.PushSubscriptionRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.ReminderSettingsRequest;
import com.jllado.weightcontrol.api.dto.PushDtos.ReminderSettingsResponse;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.PushNotificationService;
import com.jllado.weightcontrol.service.AgendaService;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushNotificationService service;
    private final CurrentUserService currentUserService;
    private final AppProperties properties;
    private final AgendaService agendaService;

    public PushController(PushNotificationService service, CurrentUserService currentUserService, AppProperties properties, AgendaService agendaService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.properties = properties;
        this.agendaService = agendaService;
    }

    @GetMapping("/config")
    public PushConfigResponse config() {
        return new PushConfigResponse(
            properties.push().enabled(),
            properties.push().enabled() ? properties.push().publicKey() : null,
            DateTimes.USER_ZONE.getId()
        );
    }

    @GetMapping("/reminder-settings")
    public ReminderSettingsResponse reminderSettings() {
        return service.reminderSettings(currentUserService.requireUser());
    }

    @GetMapping("/agenda")
    public AgendaResponse agenda() {
        return agendaService.today(currentUserService.requireUser());
    }

    @PutMapping("/reminder-settings")
    public ReminderSettingsResponse updateReminderSettings(@Valid @RequestBody ReminderSettingsRequest request) {
        return service.updateReminderSettings(currentUserService.requireUser(), request);
    }

    @PutMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody PushSubscriptionRequest request) {
        service.register(currentUserService.requireUser(), request);
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@Valid @RequestBody PushEndpointRequest request) {
        service.unregister(currentUserService.requireUser(), request.endpoint());
    }

    @PostMapping("/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(@Valid @RequestBody PushEndpointRequest request) {
        User user = currentUserService.requireUser();
        service.sendTest(user, request.endpoint());
    }

    @PostMapping("/release-notification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void notifyRelease(@Valid @RequestBody ReleaseNotificationRequest request) {
        service.sendAppUpdate(request);
    }
}
