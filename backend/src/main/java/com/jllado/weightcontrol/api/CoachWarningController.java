package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.CoachDtos.*;
import com.jllado.weightcontrol.api.dto.DashboardDtos.DashboardWarnings;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.CoachWarningService;
import com.jllado.weightcontrol.service.OvernightHeartRateService;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coach-warnings")
public class CoachWarningController {
    private final CoachWarningService service;
    private final CurrentUserService currentUser;
    private final OvernightHeartRateService heartRates;

    public CoachWarningController(CoachWarningService service, CurrentUserService currentUser, OvernightHeartRateService heartRates) {
        this.heartRates = heartRates;
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public DashboardWarnings get() {
        var user = currentUser.requireUser();
        var coach = service.overview(user);
        var analysis = heartRates.analysis(user, LocalDate.now(DateTimes.USER_ZONE));
        return new DashboardWarnings(coach.active(), coach.hasHistory(), analysis.reasons());
    }

    @GetMapping("/history")
    public WarningPage history(@RequestParam(defaultValue = "0") int page) { return service.history(currentUser.requireUser(), page); }

    @GetMapping("/{id}/revisions")
    public WarningPage revisions(@PathVariable Long id, @RequestParam(defaultValue = "0") int page) { return service.revisions(currentUser.requireUser(), id, page); }
}
