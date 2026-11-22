package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.MealRatingDtos.Preferences;
import com.jllado.weightcontrol.api.dto.MealRatingDtos.RerateResult;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.MealRatingPreferenceService;
import com.jllado.weightcontrol.service.MealRatingService;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meals")
public class MealRatingController {
    private final MealRatingPreferenceService preferences;
    private final MealRatingService ratings;
    private final CurrentUserService currentUser;

    public MealRatingController(MealRatingPreferenceService preferences, MealRatingService ratings, CurrentUserService currentUser) {
        this.preferences = preferences;
        this.ratings = ratings;
        this.currentUser = currentUser;
    }

    @GetMapping("/rating-preferences")
    public Preferences preferences() {
        return preferences.forDate(currentUser.requireUser(), LocalDate.now(DateTimes.USER_ZONE));
    }

    @PutMapping("/rating-preferences")
    public Preferences update(@Valid @RequestBody Preferences request) {
        return preferences.update(currentUser.requireUser(), request);
    }

    @PostMapping("/rerate")
    public RerateResult rerate(@RequestParam(defaultValue = "false") boolean apply) {
        return ratings.rerate(currentUser.requireUser(), apply);
    }
}
