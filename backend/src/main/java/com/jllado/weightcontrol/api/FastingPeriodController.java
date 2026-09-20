package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodRequest;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodResponse;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.FastingPeriodService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fasting-periods")
public class FastingPeriodController {

    private final FastingPeriodService service;
    private final PersonalRecordMutationService mutationService;
    private final CurrentUserService currentUserService;

    public FastingPeriodController(FastingPeriodService service, PersonalRecordMutationService mutationService, CurrentUserService currentUserService) {
        this.service = service;
        this.mutationService = mutationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<FastingPeriodResponse> all() {
        return service.findAll(currentUserService.requireUser()).stream().map(FastingPeriodResponse::from).toList();
    }

    @PostMapping
    public RecordMutationResponse<FastingPeriodResponse> create(@Valid @RequestBody FastingPeriodRequest request) {
        var result = mutationService.createFastingPeriod(currentUserService.requireUser(), request);
        return new RecordMutationResponse<>(FastingPeriodResponse.from(result.result()), result.achievements());
    }

    @PutMapping("/{id}")
    public RecordMutationResponse<FastingPeriodResponse> update(@PathVariable Long id, @Valid @RequestBody FastingPeriodRequest request) {
        var result = mutationService.updateFastingPeriod(currentUserService.requireUser(), id, request);
        return new RecordMutationResponse<>(FastingPeriodResponse.from(result.result()), result.achievements());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mutationService.deleteFastingPeriod(currentUserService.requireUser(), id);
    }
}
