package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeReasonRequest;
import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.DecisionOutcomeService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decision-outcomes")
public class DecisionOutcomeController {

    private final CurrentUserService currentUserService;
    private final PersonalRecordMutationService mutationService;
    private final DecisionOutcomeService service;

    public DecisionOutcomeController(CurrentUserService currentUserService, PersonalRecordMutationService mutationService, DecisionOutcomeService service) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.mutationService = mutationService;
    }

    @GetMapping
    public List<DecisionOutcomeResponse> history() {
        return service.history(currentUserService.requireUser()).stream().map(DecisionOutcomeResponse::from).toList();
    }

    @PutMapping("/{id}/reason")
    public DecisionOutcomeResponse updateReason(@PathVariable Long id, @Valid @RequestBody DecisionOutcomeReasonRequest request) {
        return DecisionOutcomeResponse.from(service.updateReason(currentUserService.requireUser(), id, request.reason()));
    }

    @PostMapping
    public RecordMutationResponse<DecisionOutcomeResponse> create(@Valid @RequestBody DecisionOutcomeRequest request) {
        var result = mutationService.createDecisionOutcome(currentUserService.requireUser(), request);
        return new RecordMutationResponse<>(DecisionOutcomeResponse.from(result.result()), result.achievements());
    }
}
