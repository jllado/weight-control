package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.HabitDtos.HabitRequest;
import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.User;
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

    public HabitService(HabitRepository repository) {
        this.repository = repository;
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
        Habit habit = requireOwned(user, id);
        if (habit.getLastTimeDate() != null && DateTimes.toLocalDate(habit.getLastTimeDate()).isEqual(date)) {
            throw new BadRequestException("Habit already completed for that day");
        }
        habit.setTimes(habit.getTimes() + 1);
        if (habit.getLastTimeDate() != null && ChronoUnit.DAYS.between(DateTimes.toLocalDate(habit.getLastTimeDate()), date) > 1) {
            habit.setCurrentStrike(0);
        }
        habit.setCurrentStrike(habit.getCurrentStrike() + 1);
        if (habit.getCurrentStrike() > habit.getBestStrike()) {
            habit.setBestStrike(habit.getCurrentStrike());
        }
        habit.setLastTimeDate(DateTimes.startOfDay(date));
        return repository.save(habit);
    }

    public Habit requireOwned(User user, Long id) {
        Habit habit = repository.findById(id).orElseThrow(() -> new NotFoundException("Habit not found"));
        if (!habit.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Habit not found");
        }
        return habit;
    }

    private void apply(Habit habit, HabitRequest request) {
        habit.setName(request.name());
        habit.setDuration(request.duration());
    }
}
