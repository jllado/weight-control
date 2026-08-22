package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.LipidPanelDtos.LipidPanelRequest;
import com.jllado.weightcontrol.api.dto.LipidPanelDtos.LipidPanelResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.LipidPanelService;
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
@RequestMapping("/api/lipid-panels")
public class LipidPanelController {

    private final LipidPanelService service;
    private final CurrentUserService currentUserService;
    private final PersonalRecordMutationService mutationService;

    public LipidPanelController(LipidPanelService service, CurrentUserService currentUserService, PersonalRecordMutationService mutationService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.mutationService = mutationService;
    }

    @GetMapping
    public List<LipidPanelResponse> all() {
        return service.findAll(currentUserService.requireUser()).stream().map(LipidPanelResponse::from).toList();
    }

    @PostMapping
    public RecordMutationResponse<LipidPanelResponse> create(@Valid @RequestBody LipidPanelRequest request) {
        var result = mutationService.createLipidPanel(currentUserService.requireUser(), request);
        return new RecordMutationResponse<>(LipidPanelResponse.from(result.result()), result.achievements());
    }

    @PutMapping("/{id}")
    public RecordMutationResponse<LipidPanelResponse> update(@PathVariable Long id, @Valid @RequestBody LipidPanelRequest request) {
        var result = mutationService.updateLipidPanel(currentUserService.requireUser(), id, request);
        return new RecordMutationResponse<>(LipidPanelResponse.from(result.result()), result.achievements());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mutationService.deleteLipidPanel(currentUserService.requireUser(), id);
    }
}
