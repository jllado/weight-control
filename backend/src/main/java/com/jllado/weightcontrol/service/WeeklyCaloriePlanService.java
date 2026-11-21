package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.DayPlanRequest;
import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.DayPlanResponse;
import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.WeeklyCaloriePlanRequest;
import com.jllado.weightcontrol.api.dto.WeeklyCaloriePlanDtos.WeeklyCaloriePlanResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.WeeklyCaloriePlan;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.repository.WeeklyCaloriePlanRepository;
import com.jllado.weightcontrol.service.PlanningWeekService.Period;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WeeklyCaloriePlanService {
    private final WeeklyCaloriePlanRepository repository;
    private final UserRepository users;

    private final ApplicationEventPublisher events;

    public WeeklyCaloriePlanService(WeeklyCaloriePlanRepository repository, UserRepository users, ApplicationEventPublisher events) {
        this.events = events;
        this.repository = repository;
        this.users = users;
    }

    public Optional<WeeklyCaloriePlanResponse> find(User user, LocalDate weekStart) {
        Period period = requirePeriodStart(user, weekStart);
        var rows = loadRows(user, period.start(), period.end());
        Long revision = revision(period, rows);
        return revision == null ? Optional.empty() : Optional.of(response(user, period, rows, revision));
    }

    public WeeklyCaloriePlanResponse replace(User user, WeeklyCaloriePlanRequest request) {
        User owner = users.findByIdForUpdate(user.getId()).orElseThrow();
        Period period = requirePeriodStart(owner, request.weekStart());
        validateDays(period, request.days());
        var rows = loadRows(owner, period.start(), period.end());
        if (!Objects.equals(revision(period, rows), request.revision())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This week's plan changed. Reload it and review your changes before saving again.");
        }
        for (var day : request.days()) {
            var row = rows.computeIfAbsent(storageWeek(day.date()), start -> {
                var newRow = new WeeklyCaloriePlan();
                newRow.setUser(owner);
                newRow.setWeekStart(start);
                return newRow;
            });
            row.setCaloriesOn(day.date().getDayOfWeek(), day.calories().intValueExact());
        }
        repository.saveAllAndFlush(rows.values());
        events.publishEvent(new CaloriePlanSaved(owner, period.start()));
        return response(owner, period, rows, revision(period, rows));
    }

    public List<EffectiveCalorieTarget> effectiveTargets(User user, LocalDate from, LocalDate to) {
        var rows = loadRows(user, from, to);
        var targets = new ArrayList<EffectiveCalorieTarget>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            targets.add(new EffectiveCalorieTarget(date, effectiveCalories(user, rows, date), user.getWeeklyAverageCalorieMaximum()));
        }
        return targets;
    }

    static Map<LocalDate, Integer> caloriesByDate(List<EffectiveCalorieTarget> targets) {
        return targets.stream().collect(java.util.stream.Collectors.toMap(EffectiveCalorieTarget::date, EffectiveCalorieTarget::calories));
    }

    private Map<LocalDate, WeeklyCaloriePlan> loadRows(User user, LocalDate from, LocalDate to) {
        var rows = new HashMap<LocalDate, WeeklyCaloriePlan>();
        for (var row : repository.findByUserAndWeekStartBetweenOrderByWeekStartAsc(user, storageWeek(from), storageWeek(to))) {
            rows.put(row.getWeekStart(), row);
        }
        return rows;
    }

    // Physical rows keep their original Saturday keys. Missing cells continue to follow the weekday profile.
    private static LocalDate storageWeek(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
    }

    private static Integer savedCalories(Map<LocalDate, WeeklyCaloriePlan> rows, LocalDate date) {
        var row = rows.get(storageWeek(date));
        return row == null ? null : row.caloriesOn(date.getDayOfWeek());
    }

    Integer savedCalories(User user, LocalDate date) {
        return savedCalories(loadRows(user, date, date), date);
    }

    private int effectiveCalories(User user, Map<LocalDate, WeeklyCaloriePlan> rows, LocalDate date) {
        Integer saved = savedCalories(rows, date);
        return saved == null ? profileTarget(user, date.getDayOfWeek()) : saved;
    }

    private static Long revision(Period period, Map<LocalDate, WeeklyCaloriePlan> rows) {
        boolean hasSavedDates = period.start().datesUntil(period.end().plusDays(1)).anyMatch(date -> savedCalories(rows, date) != null);
        if (!hasSavedDates) return null;
        // For a single row this is its original revision. Creation or modification of either row increases it.
        return rows.values().stream().mapToLong(row -> row.getRevision() + 1).sum() - 1;
    }

    private WeeklyCaloriePlanResponse response(User user, Period period, Map<LocalDate, WeeklyCaloriePlan> rows, Long revision) {
        var days = period.start().datesUntil(period.end().plusDays(1))
            .map(date -> new DayPlanResponse(date, effectiveCalories(user, rows, date))).toList();
        return new WeeklyCaloriePlanResponse(period.start(), revision, days);
    }

    private Period requirePeriodStart(User user, LocalDate start) {
        Period period = PlanningWeekService.period(user, start);
        if (!period.start().equals(start)) throw new BadRequestException("The requested date must start a planning week");
        return period;
    }

    private void validateDays(Period period, List<DayPlanRequest> days) {
        if (days.size() != period.end().toEpochDay() - period.start().toEpochDay() + 1) {
            throw new BadRequestException("Calorie plan must contain every date in the planning week");
        }
        for (int index = 0; index < days.size(); index++) {
            var day = days.get(index);
            if (!day.date().equals(period.start().plusDays(index))) {
                throw new BadRequestException("Calorie plan dates must be contiguous from the start of the planning week");
            }
            try {
                if (day.calories().intValueExact() < 0) throw new BadRequestException("Planned calories must be nonnegative");
            } catch (ArithmeticException exception) {
                throw new BadRequestException("Planned calories must be a whole number within the supported range");
            }
        }
    }

    static int profileTarget(User user, DayOfWeek day) {
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

    public record CaloriePlanSaved(User user, LocalDate weekStart) { }

    public record EffectiveCalorieTarget(LocalDate date, int calories, int weeklyAverageMaximum) { }
}
