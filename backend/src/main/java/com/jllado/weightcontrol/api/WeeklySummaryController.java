package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryConfigResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryArchiveResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryDetailResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryPreviewResponse;
import com.jllado.weightcontrol.api.dto.WeeklySummaryDtos.WeeklySummaryScheduleRequest;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WeeklySummaryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weekly-summary")
public class WeeklySummaryController {

    private final WeeklySummaryService service;
    private final CurrentUserService currentUserService;

    public WeeklySummaryController(WeeklySummaryService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/config")
    public WeeklySummaryConfigResponse config() {
        return service.config(currentUserService.requireUser());
    }

    @PutMapping("/config")
    public WeeklySummaryConfigResponse updateConfig(@Valid @RequestBody WeeklySummaryScheduleRequest request) {
        return service.updateDeliverySchedule(currentUserService.requireUser(), request);
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
    public WeeklySummaryPreviewResponse preview(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedFridayDate
    ) {
        var user = currentUserService.requireUser();
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
        return selectedFridayDate == null ? service.preview(user, today) : service.previewForFriday(user, selectedFridayDate, today);
    }

    @PostMapping("/create")
    public WeeklySummaryDetailResponse create(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedFridayDate
    ) {
        var user = currentUserService.requireUser();
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Madrid"));
        return selectedFridayDate == null ? service.createLatest(user, today) : service.createForFridayDetail(user, selectedFridayDate, today);
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
