package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeCreateRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeResponse;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeUpdateRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.BackPainEpisodeService;
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
@RequestMapping("/api/back-pain-episodes")
public class BackPainEpisodeController {

    private final BackPainEpisodeService service;
    private final CurrentUserService currentUserService;

    public BackPainEpisodeController(BackPainEpisodeService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<BackPainEpisodeResponse> all() {
        User user = currentUserService.requireUser();
        return service.findAll(user).stream().map(BackPainEpisodeResponse::from).toList();
    }

    @PostMapping
    public BackPainEpisodeResponse create(@Valid @RequestBody BackPainEpisodeCreateRequest request) {
        return BackPainEpisodeResponse.from(service.create(currentUserService.requireUser(), request));
    }

    @PutMapping("/{id}")
    public BackPainEpisodeResponse update(@PathVariable Long id, @Valid @RequestBody BackPainEpisodeUpdateRequest request) {
        return BackPainEpisodeResponse.from(service.update(currentUserService.requireUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(currentUserService.requireUser(), id);
    }
}
