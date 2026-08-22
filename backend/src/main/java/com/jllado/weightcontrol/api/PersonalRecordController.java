package com.jllado.weightcontrol.api;

import static com.jllado.weightcontrol.api.dto.PersonalRecordDtos.*;

import com.jllado.weightcontrol.domain.PersonalRecordDomain;
import com.jllado.weightcontrol.domain.PersonalRecordMetric;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.PersonalRecordService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/personal-records")
public class PersonalRecordController {

    private final PersonalRecordService service;
    private final CurrentUserService currentUserService;

    public PersonalRecordController(PersonalRecordService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/catalog")
    public List<CatalogMetricResponse> catalog() {
        return service.catalog(currentUserService.requireUser());
    }

    @PutMapping("/settings")
    public List<CatalogMetricResponse> replaceSettings(@Valid @RequestBody SettingsRequest request) {
        return service.replaceSettings(currentUserService.requireUser(), request);
    }

    @GetMapping("/current")
    public List<CurrentRecordResponse> current(
        @RequestParam(required = false) PersonalRecordDomain domain,
        @RequestParam(required = false) PersonalRecordMetric metric,
        @RequestParam(required = false) Long exerciseId
    ) {
        User user = currentUserService.requireUser();
        return service.current(user, domain, metric, exerciseId);
    }

    @GetMapping("/history")
    public HistoryPageResponse history(
        @RequestParam(required = false) PersonalRecordDomain domain,
        @RequestParam(required = false) PersonalRecordMetric metric,
        @RequestParam(required = false) Long exerciseId,
        @RequestParam(required = false) Set<Long> workoutId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size
    ) {
        User user = currentUserService.requireUser();
        return service.history(user, domain, metric, exerciseId, workoutId == null ? Set.of() : workoutId, page, size);
    }
}
