package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.HabitDtos.HabitRequest;
import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.HabitBaseline;
import com.jllado.weightcontrol.domain.HabitCheckin;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.HabitBaselineRepository;
import com.jllado.weightcontrol.repository.HabitCheckinRepository;
import com.jllado.weightcontrol.repository.HabitRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class HabitService {

    private final HabitRepository repository;
    private final HabitBaselineRepository baselineRepository;
    private final HabitCheckinRepository checkinRepository;

    public HabitService(HabitRepository repository, HabitBaselineRepository baselineRepository, HabitCheckinRepository checkinRepository) {
        this.repository = repository;
        this.baselineRepository = baselineRepository;
        this.checkinRepository = checkinRepository;
    }

    public List<Habit> findAll(User user) {
        return repository.findByUserOrderByStartDateAsc(user);
    }

    public Habit create(User user, HabitRequest request) {
        Habit habit = new Habit();
        habit.setUser(user);
        habit.setStartDate(DateTimes.startOfDay(user.getDashboardAnchorDate() == null ? LocalDate.now(DateTimes.USER_ZONE) : user.getDashboardAnchorDate()));
        habit.setLastTimeDate(null);
        habit.setTimes(0);
        habit.setCurrentStrike(0);
        habit.setBestStrike(0);
        apply(habit, request);
        return repository.save(habit);
    }

    public Habit update(User user, Long id, HabitRequest request) {
        Habit habit = requireOwned(user, id);
        apply(habit, request);
        return repository.save(habit);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public Habit complete(User user, Long id, LocalDate date) {
        Habit habit = requireOwnedForUpdate(user, id);
        HabitBaseline baseline = getBaseline(habit);
        if (baseline != null && baseline.getLastDate() != null && !date.isAfter(baseline.getLastDate())) {
            throw new BadRequestException("Habit completion must be after the legacy baseline date");
        }
        if (checkinRepository.existsByHabitAndCheckinDate(habit, date)) {
            throw new BadRequestException("Habit already completed for that day");
        }
        HabitCheckin checkin = new HabitCheckin();
        checkin.setHabit(habit);
        checkin.setCheckinDate(date);
        checkinRepository.save(checkin);
        rebuildSummary(habit);
        return repository.save(habit);
    }

    public Habit undoCompletion(User user, Long id, LocalDate date) {
        Habit habit = requireOwnedForUpdate(user, id);
        HabitCheckin checkin = checkinRepository.findByHabitAndCheckinDate(habit, date)
            .orElseThrow(() -> new BadRequestException("Habit check-in not found for that date"));
        checkinRepository.delete(checkin);
        rebuildSummary(habit);
        return repository.save(habit);
    }

    public List<HabitCheckin> getCheckins(Habit habit) {
        return checkinRepository.findByHabitOrderByCheckinDateAscIdAsc(habit);
    }

    public HabitBaseline getBaseline(Habit habit) {
        return baselineRepository.findByHabit(habit).orElse(null);
    }

    public Habit requireOwned(User user, Long id) {
        Habit habit = repository.findById(id).orElseThrow(() -> new NotFoundException("Habit not found"));
        if (!habit.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Habit not found");
        }
        return habit;
    }

    private Habit requireOwnedForUpdate(User user, Long id) {
        Habit habit = repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Habit not found"));
        if (!habit.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Habit not found");
        }
        return habit;
    }

    private void rebuildSummary(Habit habit) {
        HabitBaseline baseline = getBaseline(habit);
        int total = baseline == null ? 0 : baseline.getCompletionTotal();
        int current = baseline == null ? 0 : baseline.getCurrentStreak();
        int best = baseline == null ? 0 : baseline.getBestStreak();
        LocalDate lastDate = baseline == null ? null : baseline.getLastDate();
        for (HabitCheckin checkin : getCheckins(habit)) {
            LocalDate date = checkin.getCheckinDate();
            total++;
            current = lastDate != null && ChronoUnit.DAYS.between(lastDate, date) == 1 ? current + 1 : 1;
            best = Math.max(best, current);
            lastDate = date;
        }
        habit.setTimes(total);
        habit.setCurrentStrike(current);
        habit.setBestStrike(best);
        habit.setLastTimeDate(lastDate == null ? null : DateTimes.startOfDay(lastDate));
    }

    private void apply(Habit habit, HabitRequest request) {
        habit.setName(request.name());
        habit.setDuration(request.duration());
    }
}
