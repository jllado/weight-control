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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class RoutineService {

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
        Routine routine = requireOwned(user, id);
        if (checkinRepository.existsByRoutineAndCheckedAt(routine, checkedAt)) {
            throw new BadRequestException("Routine already completed for that timestamp");
        }
        RoutineCheckin checkin = new RoutineCheckin();
        checkin.setRoutine(routine);
        checkin.setCheckedAt(checkedAt);
        checkinRepository.save(checkin);

        LocalDate checkinDate = DateTimes.toLocalDate(checkedAt);
        if (routine.getLastTimeDate() != null && ChronoUnit.DAYS.between(DateTimes.toLocalDate(routine.getLastTimeDate()), checkinDate) > 1) {
            routine.setCurrentStrike(0);
        }
        routine.setCurrentStrike(routine.getCurrentStrike() + 1);
        if (routine.getCurrentStrike() > routine.getBestStrike()) {
            routine.setBestStrike(routine.getCurrentStrike());
        }
        routine.setLastTimeDate(checkedAt);
        return repository.save(routine);
    }

    public Routine requireOwned(User user, Long id) {
        Routine routine = repository.findById(id).orElseThrow(() -> new NotFoundException("Routine not found"));
        if (!routine.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Routine not found");
        }
        return routine;
    }

    private void apply(Routine routine, RoutineRequest request) {
        routine.setName(request.name());
        routine.setTypes(request.types());
    }
}
