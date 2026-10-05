package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryConfigResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryArchiveResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryDetailResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryPreviewResponse;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WeeklySummaryService;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
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
        var user = currentUserService.requireUser();
        boolean canSend = user.getEmail().equalsIgnoreCase(properties.weeklySummary().ownerEmail());
        return new WeeklySummaryConfigResponse(
            properties.weeklySummary().enabled(),
            canSend,
            canSend ? properties.weeklySummary().recipientEmail() : null,
            WeeklySummaryService.DELIVERY_DAY,
            WeeklySummaryService.DELIVERY_TIME,
            DateTimes.USER_ZONE.getId()
        );
    }

    @GetMapping
    public WeeklySummaryArchiveResponse archive(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedFridayDate
    ) {
        return service.archive(currentUserService.requireUser(), page, size, selectedFridayDate);
    }

    @GetMapping("/preview")
    public WeeklySummaryPreviewResponse preview() {
        return service.preview(currentUserService.requireUser(), LocalDate.now(ZoneId.of("Europe/Madrid")));
    }

    @PostMapping("/create")
    public WeeklySummaryDetailResponse create() {
        return service.createLatest(currentUserService.requireUser(), LocalDate.now(ZoneId.of("Europe/Madrid")));
    }

    @GetMapping("/{fridayDate}")
    public ResponseEntity<WeeklySummaryDetailResponse> get(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fridayDate
    ) {
        return service.detail(currentUserService.requireUser(), fridayDate)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/send")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send() {
        service.send(currentUserService.requireUser());
    }
}
