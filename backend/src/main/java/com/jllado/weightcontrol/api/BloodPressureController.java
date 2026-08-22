package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.BloodPressureDtos.BloodPressureRequest;
import com.jllado.weightcontrol.api.dto.BloodPressureDtos.BloodPressureResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.BloodPressureService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/blood-pressures")
public class BloodPressureController {

    private final BloodPressureService service;
    private final CurrentUserService currentUserService;
    private final PersonalRecordMutationService mutationService;

    public BloodPressureController(BloodPressureService service, CurrentUserService currentUserService, PersonalRecordMutationService mutationService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.mutationService = mutationService;
    }

    @GetMapping
    public List<BloodPressureResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(BloodPressureResponse::from).toList();
    }

    @PostMapping
    public RecordMutationResponse<BloodPressureResponse> create(@Valid @RequestBody BloodPressureRequest request) {
        User user = currentUserService.requireUser();
        var result = mutationService.createBloodPressure(user, request);
        return new RecordMutationResponse<>(BloodPressureResponse.from(result.result()), result.achievements());
    }

    @PutMapping("/{id}")
    public RecordMutationResponse<BloodPressureResponse> update(@PathVariable Long id, @Valid @RequestBody BloodPressureRequest request) {
        User user = currentUserService.requireUser();
        var result = mutationService.updateBloodPressure(user, id, request);
        return new RecordMutationResponse<>(BloodPressureResponse.from(result.result()), result.achievements());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        User user = currentUserService.requireUser();
        mutationService.deleteBloodPressure(user, id);
    }
}
