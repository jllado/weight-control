package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.MoodDtos.MoodRequest;
import com.jllado.weightcontrol.api.dto.MoodDtos.MoodResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.MoodService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moods")
public class MoodController {

    private final MoodService service;
    private final CurrentUserService currentUserService;

    public MoodController(MoodService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<MoodResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(MoodResponse::from).toList();
    }

    @PostMapping
    public MoodResponse create(@Valid @RequestBody MoodRequest request) {
        return MoodResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public MoodResponse update(@PathVariable Long id, @Valid @RequestBody MoodRequest request) {
        return MoodResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
