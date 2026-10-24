package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.ActionDtos.*;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/actions")
public class ActionController {
    private final ActionService service;
    private final ActionRecommendationService recommendations;
    private final CurrentUserService currentUser;
    public ActionController(ActionService service, ActionRecommendationService recommendations, CurrentUserService currentUser) {
        this.service = service; this.recommendations = recommendations; this.currentUser = currentUser;
    }
    @GetMapping public List<ActionResponse> list(@RequestParam LocalDate date, @RequestParam(defaultValue = "ACTIVE") String view) {
        return service.list(currentUser.requireUser(), date, view);
    }
    @GetMapping("/{id}") public ActionResponse get(@PathVariable Long id, @RequestParam LocalDate date) { return service.find(currentUser.requireUser(), id, date); }
    @GetMapping("/suggestions") public List<SuggestionResponse> suggestions() { return recommendations.suggestions(currentUser.requireUser()); }
    @GetMapping("/suggestions/{sourceKey}") public SuggestionResponse source(@PathVariable String sourceKey) { return recommendations.resolve(currentUser.requireUser(), sourceKey); }
    @PostMapping public ActionResponse create(@Valid @RequestBody ActionRequest request) { return service.create(currentUser.requireUser(), request); }
    @PutMapping("/{id}") public ActionResponse update(@PathVariable Long id, @Valid @RequestBody ActionRequest request) { return service.update(currentUser.requireUser(), id, request); }
    @PostMapping("/{id}/stop") public ActionResponse stop(@PathVariable Long id) { return service.stop(currentUser.requireUser(), id); }
    @PostMapping("/{id}/restart") public ActionResponse restart(@PathVariable Long id, @Valid @RequestBody ActionRequest request) { return service.restart(currentUser.requireUser(), id, request); }
    @PutMapping("/{id}/checkins/{date}") public ActionResponse check(@PathVariable Long id, @PathVariable LocalDate date) { return service.check(currentUser.requireUser(), id, date, true); }
    @DeleteMapping("/{id}/checkins/{date}") public ActionResponse undo(@PathVariable Long id, @PathVariable LocalDate date) { return service.check(currentUser.requireUser(), id, date, false); }
}
