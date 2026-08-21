package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.NutritionDtos.DailyNutritionSummaryResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.NutritionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {

    private final NutritionService service;
    private final CurrentUserService currentUserService;

    public NutritionController(NutritionService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/daily-summaries")
    public List<DailyNutritionSummaryResponse> dailySummaries() {
        return service.findAll(currentUserService.requireUser()).stream().map(DailyNutritionSummaryResponse::from).toList();
    }
}
