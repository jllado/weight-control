package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.NutritionDtos.DailyNutritionSummaryResponse;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetOverrides;
import com.jllado.weightcontrol.api.dto.NutritionDtos.NutrientTargetSettingsResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.NutrientTargetService;
import com.jllado.weightcontrol.service.NutritionService;
import com.jllado.weightcontrol.service.BadRequestException;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {

    private final NutritionService service;
    private final NutrientTargetService targetService;
    private final CurrentUserService currentUserService;

    public NutritionController(NutritionService service, NutrientTargetService targetService, CurrentUserService currentUserService) {
        this.service = service;
        this.targetService = targetService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/daily-summaries")
    public List<DailyNutritionSummaryResponse> dailySummaries(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var user = currentUserService.requireUser();
        if (from == null && to == null) return service.findAll(user).stream().map(DailyNutritionSummaryResponse::from).toList();
        if (from == null || to == null) throw new BadRequestException("Both from and to dates are required");
        return service.findBetween(user, from, to).stream().map(DailyNutritionSummaryResponse::from).toList();
    }

    @GetMapping("/targets")
    public NutrientTargetSettingsResponse targets(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        return targetService.settings(currentUserService.requireUser(), asOf == null ? LocalDate.now(DateTimes.USER_ZONE) : asOf);
    }

    @PutMapping("/targets")
    public NutrientTargetSettingsResponse updateTargets(@Valid @RequestBody NutrientTargetOverrides request) {
        return targetService.update(currentUserService.requireUser(), request, LocalDate.now(DateTimes.USER_ZONE));
    }
}
