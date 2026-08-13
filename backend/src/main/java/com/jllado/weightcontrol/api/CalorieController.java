package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CalorieDtos.CalorieResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CalorieService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calories")
public class CalorieController {

    private final CalorieService service;
    private final CurrentUserService currentUserService;

    public CalorieController(CalorieService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<CalorieResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(CalorieResponse::from).toList();
    }
}
