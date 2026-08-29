package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationDoseActionRequest;
import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationDoseResponse;
import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationDoseSnoozeRequest;
import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationDoseSnoozeResponse;
import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationRequest;
import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationReminderTimeRequest;
import com.jllado.weightcontrol.api.dto.MedicationDtos.MedicationResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.MedicationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medications")
public class MedicationController {

    private final MedicationService service;
    private final CurrentUserService currentUserService;

    public MedicationController(MedicationService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<MedicationResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(MedicationResponse::from).toList();
    }

    @PostMapping
    public MedicationResponse create(@Valid @RequestBody MedicationRequest request) {
        return MedicationResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public MedicationResponse update(@PathVariable Long id, @Valid @RequestBody MedicationRequest request) {
        return MedicationResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @PutMapping("/{id}/reminder-times")
    public MedicationResponse updateReminderTime(@PathVariable Long id, @Valid @RequestBody MedicationReminderTimeRequest request) {
        return MedicationResponse.from(service.updateReminderTime(currentUserService.requireUser(), id, request.oldTime(), request.time()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }

    @GetMapping("/doses")
    public List<MedicationDoseResponse> doses(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        User user = currentUserService.requireUser();
        return service.findDoses(user, from, to).stream().map(MedicationDoseResponse::from).toList();
    }

    @GetMapping("/doses/{id}")
    public MedicationDoseResponse dose(@PathVariable Long id) {
        return MedicationDoseResponse.from(service.findDose(currentUserService.requireUser(), id));
    }

    @PostMapping("/{id}/doses")
    public MedicationDoseResponse logDose(@PathVariable Long id, @Valid @RequestBody MedicationDoseActionRequest request) {
        return MedicationDoseResponse.from(service.logDose(currentUserService.requireUser(), id, request.takenAt()));
    }

    @PostMapping("/doses/{id}/take")
    public MedicationDoseResponse takeDose(@PathVariable Long id, @Valid @RequestBody MedicationDoseActionRequest request) {
        return MedicationDoseResponse.from(service.takeDose(currentUserService.requireUser(), id, request.takenAt()));
    }

    @PostMapping("/doses/{id}/snooze")
    public MedicationDoseSnoozeResponse snoozeDose(@PathVariable Long id, @Valid @RequestBody MedicationDoseSnoozeRequest request) {
        return new MedicationDoseSnoozeResponse(service.snoozeDose(currentUserService.requireUser(), id, request.minutes()));
    }
}
