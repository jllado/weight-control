package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.MoodDtos.MoodRequest;
import com.jllado.weightcontrol.api.dto.MoodDtos.MoodResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.MoodService;
import com.jllado.weightcontrol.service.PersonalRecordMutationService;
import com.jllado.weightcontrol.api.dto.PersonalRecordDtos.RecordMutationResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moods")
public class MoodController {

    private final MoodService service;
    private final CurrentUserService currentUserService;
    private final PersonalRecordMutationService mutationService;

    public MoodController(MoodService service, CurrentUserService currentUserService, PersonalRecordMutationService mutationService) {
        this.service = service;
        this.currentUserService = currentUserService;
        this.mutationService = mutationService;
    }

    @GetMapping
    public List<MoodResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(MoodResponse::from).toList();
    }

    @PostMapping
    public RecordMutationResponse<MoodResponse> create(@Valid @RequestBody MoodRequest request) {
        var result = mutationService.createMood(currentUserService.requireUser(), request);
        return new RecordMutationResponse<>(MoodResponse.from(result.result()), result.achievements());
    }

    @PutMapping("/{id}")
    public RecordMutationResponse<MoodResponse> update(@PathVariable Long id, @Valid @RequestBody MoodRequest request) {
        var result = mutationService.updateMood(currentUserService.requireUser(), id, request);
        return new RecordMutationResponse<>(MoodResponse.from(result.result()), result.achievements());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        mutationService.deleteMood(currentUserService.requireUser(), id);
    }
}
