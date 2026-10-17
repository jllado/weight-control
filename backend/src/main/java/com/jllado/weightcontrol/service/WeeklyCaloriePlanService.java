package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.DayPlanRequest;
import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.WeeklyCaloriePlanRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyCaloriePlan;
import com.jllado.weightcontrol.repository.WeeklyCaloriePlanRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WeeklyCaloriePlanService {
    private final WeeklyCaloriePlanRepository repository;

    public WeeklyCaloriePlanService(WeeklyCaloriePlanRepository repository) { this.repository = repository; }

    public Optional<WeeklyCaloriePlan> find(User user, LocalDate weekStart) {
        requireSaturday(weekStart);
        return repository.findByUserAndWeekStart(user, weekStart);
    }

    public WeeklyCaloriePlan replace(User user, WeeklyCaloriePlanRequest request) {
        requireSaturday(request.weekStart());
        List<Integer> values = validateDays(request.weekStart(), request.days());
        WeeklyCaloriePlan plan = repository.findByUserAndWeekStart(user, request.weekStart()).orElse(null);
        if ((plan == null && request.revision() != null) || (plan != null && !plan.getRevision().equals(request.revision()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This week's plan changed. Reload it and review your changes before saving again.");
        }
        if (plan == null) {
            plan = new WeeklyCaloriePlan();
            plan.setUser(user);
            plan.setWeekStart(request.weekStart());
        }
        plan.setCalories(values);
        return repository.save(plan);
    }

    public List<EffectiveCalorieTarget> effectiveTargets(User user, LocalDate from, LocalDate to) {
        LocalDate firstWeek = saturdayOnOrBefore(from);
        LocalDate lastWeek = saturdayOnOrBefore(to);
        Map<LocalDate, WeeklyCaloriePlan> plans = repository.findByUserAndWeekStartBetweenOrderByWeekStartAsc(user, firstWeek, lastWeek)
            .stream().collect(Collectors.toMap(WeeklyCaloriePlan::getWeekStart, Function.identity()));
        List<EffectiveCalorieTarget> targets = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            WeeklyCaloriePlan plan = plans.get(saturdayOnOrBefore(date));
            int target = plan == null ? profileTarget(user, date.getDayOfWeek()) : plan.getCalories().get((int) (date.toEpochDay() - saturdayOnOrBefore(date).toEpochDay()));
            targets.add(new EffectiveCalorieTarget(date, target, user.getWeeklyAverageCalorieMaximum()));
        }
        return targets;
    }

    private List<Integer> validateDays(LocalDate weekStart, List<DayPlanRequest> days) {
        if (days.size() != 7) {
            throw new BadRequestException("Calorie plan must contain all seven dates from Saturday through Friday");
        }
        List<Integer> values = new ArrayList<>(7);
        for (int index = 0; index < 7; index++) {
            DayPlanRequest day = days.get(index);
            if (!day.date().equals(weekStart.plusDays(index))) {
                throw new BadRequestException("Calorie plan dates must be contiguous from Saturday through Friday");
            }
            try {
                int calories = day.calories().intValueExact();
                if (calories < 0) throw new BadRequestException("Planned calories must be nonnegative");
                values.add(calories);
            } catch (ArithmeticException exception) {
                throw new BadRequestException("Planned calories must be a whole number within the supported range");
            }
        }
        return values;
    }

    private void requireSaturday(LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            throw new BadRequestException("Calorie plan week must start on Saturday");
        }
    }

    private int profileTarget(User user, DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> user.getTypicalCaloriesSaturday();
            case SUNDAY -> user.getTypicalCaloriesSunday();
            case MONDAY -> user.getTypicalCaloriesMonday();
            case TUESDAY -> user.getTypicalCaloriesTuesday();
            case WEDNESDAY -> user.getTypicalCaloriesWednesday();
            case THURSDAY -> user.getTypicalCaloriesThursday();
            case FRIDAY -> user.getTypicalCaloriesFriday();
        };
    }

    private LocalDate saturdayOnOrBefore(LocalDate date) {
        return date.minusDays((date.getDayOfWeek().getValue() + 1L) % 7);
    }

    public record EffectiveCalorieTarget(LocalDate date, int calories, int weeklyAverageMaximum) { }
}
