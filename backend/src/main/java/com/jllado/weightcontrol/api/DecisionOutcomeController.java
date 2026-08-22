package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeRequest;
import com.jllado.weightcontrol.api.dto.DecisionOutcomeDtos.DecisionOutcomeResponse;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/decision-outcomes")
public class DecisionOutcomeController {

    private final CurrentUserService currentUserService;
    private final PersonalRecordMutationService mutationService;

    public DecisionOutcomeController(CurrentUserService currentUserService, PersonalRecordMutationService mutationService) {
        this.currentUserService = currentUserService;
        this.mutationService = mutationService;
    }

    @PostMapping
    public RecordMutationResponse<DecisionOutcomeResponse> create(@Valid @RequestBody DecisionOutcomeRequest request) {
        var result = mutationService.createDecisionOutcome(currentUserService.requireUser(), request);
        return new RecordMutationResponse<>(DecisionOutcomeResponse.from(result.result()), result.achievements());
    }
}
