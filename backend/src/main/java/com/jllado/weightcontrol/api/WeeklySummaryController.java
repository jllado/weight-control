package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryConfigResponse;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WeeklySummaryService;
import com.jllado.weightcontrol.util.DateTimes;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weekly-summary")
public class WeeklySummaryController {

    private final WeeklySummaryService service;
    private final CurrentUserService currentUserService;
    private final AppProperties properties;

    public WeeklySummaryController(WeeklySummaryService service, CurrentUserService currentUserService, AppProperties properties) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.properties = properties;
    }

    @GetMapping("/config")
    public WeeklySummaryConfigResponse config() {
        return new WeeklySummaryConfigResponse(
            properties.weeklySummary().enabled(),
            properties.weeklySummary().recipientEmail(),
            WeeklySummaryService.DELIVERY_DAY,
            WeeklySummaryService.DELIVERY_TIME,
            DateTimes.USER_ZONE.getId()
        );
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send() {
        service.send(currentUserService.requireUser());
    }
}
