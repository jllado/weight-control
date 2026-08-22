package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.api.dto.SleepDtos.SleepResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.SleepService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
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
@RequestMapping("/api/sleeps")
public class SleepController {

    private final SleepService sleepService;
    private final CurrentUserService currentUserService;
    private final PersonalRecordMutationService mutationService;

    public SleepController(SleepService sleepService, CurrentUserService currentUserService, PersonalRecordMutationService mutationService) {
        this.sleepService = sleepService;
        this.currentUserService = currentUserService;
        this.mutationService = mutationService;
    }

    @GetMapping
    public List<SleepResponse> all() {
        User user = currentUserService.requireUser();
        return sleepService.findAll(user).stream().map(SleepResponse::from).toList();
    }

    @PostMapping
    public RecordMutationResponse<SleepResponse> create(@Valid @RequestBody SleepRequest request) {
        var result = mutationService.createSleep(currentUserService.requireUser(), request);
        return new RecordMutationResponse<>(SleepResponse.from(result.result()), result.achievements());
    }

    @PutMapping("/{id}")
    public RecordMutationResponse<SleepResponse> update(@PathVariable Long id, @Valid @RequestBody SleepRequest request) {
        var result = mutationService.updateSleep(currentUserService.requireUser(), id, request);
        return new RecordMutationResponse<>(SleepResponse.from(result.result()), result.achievements());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mutationService.deleteSleep(currentUserService.requireUser(), id);
    }
}
