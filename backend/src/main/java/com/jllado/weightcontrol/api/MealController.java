package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.MealDtos.MealRequest;
import com.jllado.weightcontrol.api.dto.MealDtos.MealResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.MealService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meals")
public class MealController {

    private final MealService service;
    private final CurrentUserService currentUserService;
    private final PersonalRecordMutationService mutationService;

    public MealController(MealService service, CurrentUserService currentUserService, PersonalRecordMutationService mutationService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.mutationService = mutationService;
    }

    @GetMapping
    public List<MealResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(MealResponse::from).toList();
    }

    @PostMapping
    public RecordMutationResponse<MealResponse> create(@Valid @RequestBody MealRequest request) {
        var result = mutationService.createMeal(currentUserService.requireUser(), request);
        return new RecordMutationResponse<>(MealResponse.from(result.result()), result.achievements());
    }

    @PutMapping("/{id}")
    public RecordMutationResponse<MealResponse> update(@PathVariable Long id, @Valid @RequestBody MealRequest request) {
        var result = mutationService.updateMeal(currentUserService.requireUser(), id, request);
        return new RecordMutationResponse<>(MealResponse.from(result.result()), result.achievements());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mutationService.deleteMeal(currentUserService.requireUser(), id);
    }
}
