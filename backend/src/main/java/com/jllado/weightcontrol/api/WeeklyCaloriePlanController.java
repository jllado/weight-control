package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.WeeklyCaloriePlanRequest;
import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.WeeklyCaloriePlanResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.WeeklyCaloriePlanService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weekly-calorie-plans")
public class WeeklyCaloriePlanController {
    private final WeeklyCaloriePlanService service;
    private final CurrentUserService currentUserService;

    public WeeklyCaloriePlanController(WeeklyCaloriePlanService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{weekStart}")
    public ResponseEntity<WeeklyCaloriePlanResponse> get(@PathVariable LocalDate weekStart) {
        return service.find(currentUserService.requireUser(), weekStart).map(WeeklyCaloriePlanResponse::from)
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{weekStart}")
    public WeeklyCaloriePlanResponse replace(@PathVariable LocalDate weekStart, @Valid @RequestBody WeeklyCaloriePlanRequest request) {
        if (!weekStart.equals(request.weekStart())) {
            throw new com.jllado.weightcontrol.service.BadRequestException("Calorie plan URL and body week must match");
        }
        return WeeklyCaloriePlanResponse.from(service.replace(currentUserService.requireUser(), request));
    }
}
