package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.repository.UserRepository;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty("app.meal-rating.rerate.mode")
record MealRatingRerateRunner(MealRatingService ratings, UserRepository users,
    @Value("${app.meal-rating.rerate.mode}") String mode,
    @Value("${app.meal-rating.rerate.owner-email}") String email,
    @Value("${app.meal-rating.rerate.date}") LocalDate date) implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments arguments) {
        boolean apply = switch (mode) {
            case "dry-run" -> false;
            case "apply" -> true;
            default -> throw new IllegalArgumentException("Meal rating mode must be dry-run or apply");
        };
        var owner = users.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("Meal rating owner not found"));
        var changes = ratings.rerateDay(owner, date, apply);
        System.out.printf("Meal rating %s for %s on %s: %d eligible%n", mode, email, date, changes.size());
        changes.forEach(change -> System.out.printf("%s  %s -> %s%n", change.time(), change.previous(), change.score()));
    }
}
