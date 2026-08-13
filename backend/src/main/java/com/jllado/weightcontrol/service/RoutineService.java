package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class RoutineService {

    private static final Set<Integer> SNOOZE_MINUTES = Set.of(15, 30, 60);

    private final RoutineRepository repository;
    private final RoutineCheckinRepository checkinRepository;

    public RoutineService(RoutineRepository repository, RoutineCheckinRepository checkinRepository) {
        this.repository = repository;
        this.checkinRepository = checkinRepository;
    }

    public List<Routine> findAll(User user) {
        return repository.findByUserOrderByStartDateAsc(user);
    }

    public List<OffsetDateTime> getCheckins(Routine routine) {
        return checkinRepository.findByRoutineOrderByCheckedAtAsc(routine).stream().map(RoutineCheckin::getCheckedAt).toList();
    }

    public Routine create(User user, RoutineRequest request) {
        Routine routine = new Routine();
        routine.setUser(user);
        routine.setStartDate(DateTimes.startOfDay(user.getDashboardAnchorDate() == null ? LocalDate.now(DateTimes.USER_ZONE) : user.getDashboardAnchorDate()));
        routine.setCurrentStrike(0);
        routine.setBestStrike(0);
        routine.setLastTimeDate(null);
        apply(routine, request);
        return repository.save(routine);
    }

    public Routine update(User user, Long id, RoutineRequest request) {
        Routine routine = requireOwned(user, id);
        apply(routine, request);
        return repository.save(routine);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public Routine checkin(User user, Long id, OffsetDateTime checkedAt) {
        Routine routine = requireOwnedForUpdate(user, id);
        LocalDate checkedDate = DateTimes.toLocalDate(checkedAt);
        if (checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine,
            DateTimes.startOfDay(checkedDate),
            DateTimes.startOfDay(checkedDate.plusDays(1))
        )) {
            return routine;
        }
        RoutineCheckin checkin = new RoutineCheckin();
        checkin.setRoutine(routine);
        checkin.setCheckedAt(checkedAt);
        checkinRepository.save(checkin);

        routine.setReminderSnoozedUntil(null);
        applyCheckinSummary(routine, checkedAt);
        return repository.save(routine);
    }

    public OffsetDateTime snoozeReminder(User user, Long id, int minutes) {
        return snoozeReminder(user, id, minutes, ZonedDateTime.now(DateTimes.USER_ZONE));
    }

    OffsetDateTime snoozeReminder(User user, Long id, int minutes, ZonedDateTime now) {
        if (!SNOOZE_MINUTES.contains(minutes)) {
            throw new BadRequestException("Routine reminder snooze must be 15, 30, or 60 minutes");
        }
        Routine routine = requireOwnedForUpdate(user, id);
        LocalDate date = now.toLocalDate();
        if (routine.getReminderTime() == null || DateTimes.toLocalDate(routine.getStartDate()).isAfter(date) || isCompleted(routine, date)) {
            throw new BadRequestException("Routine reminder is not active");
        }
        ZonedDateTime nextReminderAt = now.plusMinutes(minutes);
        OffsetDateTime storedReminderAt = nextReminderAt.toLocalDate().equals(date) ? nextReminderAt.toOffsetDateTime() : null;
        routine.setReminderSnoozedUntil(storedReminderAt);
        repository.save(routine);
        return storedReminderAt;
    }

    public Routine undoCheckin(User user, Long id, OffsetDateTime checkedAt) {
        Routine routine = requireOwned(user, id);
        RoutineCheckin checkin = checkinRepository.findByRoutineAndCheckedAt(routine, checkedAt)
            .orElseThrow(() -> new BadRequestException("Routine check-in not found for that timestamp"));
        checkinRepository.delete(checkin);
        rebuildSummary(routine);
        return repository.save(routine);
    }

    public Routine requireOwned(User user, Long id) {
        Routine routine = repository.findById(id).orElseThrow(() -> new NotFoundException("Routine not found"));
        requireOwner(user, routine);
        return routine;
    }

    private Routine requireOwnedForUpdate(User user, Long id) {
        Routine routine = repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Routine not found"));
        requireOwner(user, routine);
        return routine;
    }

    private void requireOwner(User user, Routine routine) {
        if (!routine.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Routine not found");
        }
    }

    private void apply(Routine routine, RoutineRequest request) {
        LocalTime reminderTime = request.reminderTime() == null ? null : request.reminderTime().truncatedTo(ChronoUnit.MINUTES);
        if (!Objects.equals(routine.getReminderTime(), reminderTime)) {
            routine.setReminderSnoozedUntil(null);
        }
        routine.setName(request.name());
        routine.setTypes(request.types());
        routine.setReminderTime(reminderTime);
    }

    private boolean isCompleted(Routine routine, LocalDate date) {
        return checkinRepository.existsByRoutineAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            routine,
            DateTimes.startOfDay(date),
            DateTimes.startOfDay(date.plusDays(1))
        );
    }

    private void rebuildSummary(Routine routine) {
        routine.setCurrentStrike(0);
        routine.setBestStrike(0);
        routine.setLastTimeDate(null);
        for (RoutineCheckin checkin : checkinRepository.findByRoutineOrderByCheckedAtAsc(routine)) {
            applyCheckinSummary(routine, checkin.getCheckedAt());
        }
    }

    private void applyCheckinSummary(Routine routine, OffsetDateTime checkedAt) {
        LocalDate checkinDate = DateTimes.toLocalDate(checkedAt);
        if (routine.getLastTimeDate() != null && ChronoUnit.DAYS.between(DateTimes.toLocalDate(routine.getLastTimeDate()), checkinDate) > 1) {
            routine.setCurrentStrike(0);
        }
        routine.setCurrentStrike(routine.getCurrentStrike() + 1);
        if (routine.getCurrentStrike() > routine.getBestStrike()) {
            routine.setBestStrike(routine.getCurrentStrike());
        }
        routine.setLastTimeDate(checkedAt);
    }
}
