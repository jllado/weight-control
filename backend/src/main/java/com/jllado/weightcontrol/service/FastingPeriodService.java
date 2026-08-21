package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.CoachFastingPeriodRequest;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodRequest;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.FastingPeriodRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class FastingPeriodService {

    private final FastingPeriodRepository repository;

    public FastingPeriodService(FastingPeriodRepository repository) {
        this.repository = repository;
    }

    public List<FastingPeriod> findAll(User user) {
        return repository.findByUserOrderByStartTimeDescIdDesc(user);
    }

    public List<FastingPeriod> findBetween(User user, LocalDate from, LocalDate to) {
        return repository.findByUserAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAscIdAsc(
            user,
            DateTimes.startOfDay(to).plusDays(1),
            DateTimes.startOfDay(from)
        );
    }

    public long count(User user) {
        return repository.countByUser(user);
    }

    public Optional<LocalDate> findFirstRecordedDate(User user) {
        return repository.findFirstByUserOrderByStartTimeAscIdAsc(user)
            .map(period -> DateTimes.toLocalDate(period.getStartTime()));
    }

    public Optional<LocalDate> findLastRecordedDate(User user) {
        return repository.findFirstByUserOrderByEndTimeDescIdDesc(user)
            .map(period -> DateTimes.toLocalDate(period.getEndTime()));
    }

    public FastingPeriod create(User user, FastingPeriodRequest request) {
        validate(request, user, null);
        FastingPeriod period = new FastingPeriod();
        period.setUser(user);
        apply(period, request);
        return repository.save(period);
    }

    public FastingPeriod update(User user, Long id, FastingPeriodRequest request) {
        FastingPeriod period = requireOwned(user, id);
        validate(request, user, id);
        apply(period, request);
        return repository.save(period);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public FastingPeriod createConfirmed(User user, CoachFastingPeriodRequest request) {
        requireConfirmation(request.confirmed());
        return create(user, request.period());
    }

    public FastingPeriod updateConfirmed(User user, Long id, CoachFastingPeriodRequest request) {
        requireConfirmation(request.confirmed());
        return update(user, id, request.period());
    }

    public void deleteConfirmed(User user, Long id, boolean confirmed) {
        requireConfirmation(confirmed);
        delete(user, id);
    }

    private FastingPeriod requireOwned(User user, Long id) {
        FastingPeriod period = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Fasting period not found"));
        if (!period.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Fasting period not found");
        }
        return period;
    }

    private void apply(FastingPeriod period, FastingPeriodRequest request) {
        period.setStartTime(request.startTime());
        period.setEndTime(request.endTime());
        period.setNotes(request.notes());
    }

    private void validate(FastingPeriodRequest request, User user, Long excludedId) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("Fasting period start time must be before the end time");
        }
        if (request.endTime().isAfter(OffsetDateTime.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Fasting period end time cannot be in the future");
        }
        if (repository.existsOverlapping(user, request.startTime(), request.endTime(), excludedId)) {
            throw new BadRequestException("Fasting period overlaps an existing period");
        }
    }

    private void requireConfirmation(boolean confirmed) {
        if (!confirmed) {
            throw new BadRequestException("Fasting period write requires explicit confirmation");
        }
    }
}
