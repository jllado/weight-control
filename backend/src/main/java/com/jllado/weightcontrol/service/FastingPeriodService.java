package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.CoachFastingPeriodRequest;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodRequest;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.FastingPeriodSource;
import com.jllado.weightcontrol.domain.Meal;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.FastingPeriodRepository;
import com.jllado.weightcontrol.repository.MealRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class FastingPeriodService {

    private static final Duration AUTOMATIC_MINIMUM_DURATION = Duration.ofHours(12);

    private final FastingPeriodRepository repository;
    private final MealRepository mealRepository;

    public FastingPeriodService(FastingPeriodRepository repository, MealRepository mealRepository) {
        this.repository = repository;
        this.mealRepository = mealRepository;
    }

    public List<FastingPeriod> findAll(User user) {
        return repository.findByUserOrderByStartTimeDescIdDesc(user);
    }

    public List<FastingPeriod> findBetween(User user, LocalDate from, LocalDate to) {
        return repository.findBetween(
            user,
            DateTimes.startOfDay(to).plusDays(1),
            DateTimes.startOfDay(from)
        );
    }

    public Optional<FastingPeriod> findActiveAutomaticPeriod(User user) {
        return repository.findFirstByUserAndSourceAndEndTimeIsNullOrderByStartTimeDescIdDesc(user, FastingPeriodSource.AUTOMATIC);
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
            .map(FastingPeriod::getEndTime)
            .filter(java.util.Objects::nonNull)
            .map(DateTimes::toLocalDate);
    }

    public FastingPeriod create(User user, FastingPeriodRequest request) {
        validate(request, user, null);
        FastingPeriod period = new FastingPeriod();
        period.setUser(user);
        period.setSource(FastingPeriodSource.MANUAL);
        apply(period, request);
        return repository.save(period);
    }

    public FastingPeriod update(User user, Long id, FastingPeriodRequest request) {
        FastingPeriod period = requireOwned(user, id);
        requireManual(period);
        validate(request, user, id);
        apply(period, request);
        return repository.save(period);
    }

    public void delete(User user, Long id) {
        FastingPeriod period = requireOwned(user, id);
        requireManual(period);
        repository.delete(period);
    }

    public void recalculateAutomaticPeriods(User user) {
        repository.deleteByUserAndSource(user, FastingPeriodSource.AUTOMATIC);
        List<Meal> meals = mealRepository.findByUserAndMealTimeIsNotNullOrderByMealDateAscMealTimeAscIdAsc(user);
        OffsetDateTime now = OffsetDateTime.now(DateTimes.USER_ZONE);
        for (int index = 0; index < meals.size(); index++) {
            OffsetDateTime start = timestamp(meals.get(index));
            OffsetDateTime end = index + 1 < meals.size() ? timestamp(meals.get(index + 1)) : null;
            Duration duration = Duration.between(start, end == null ? now : end);
            if (!duration.isNegative() && duration.compareTo(AUTOMATIC_MINIMUM_DURATION) >= 0) {
                FastingPeriod period = new FastingPeriod();
                period.setUser(user);
                period.setSource(FastingPeriodSource.AUTOMATIC);
                period.setStartTime(start);
                period.setEndTime(end);
                repository.save(period);
            }
        }
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

    private void requireManual(FastingPeriod period) {
        if (period.getSource() != FastingPeriodSource.MANUAL) {
            throw new BadRequestException("Automatic fasting periods are updated from meals");
        }
    }

    private void apply(FastingPeriod period, FastingPeriodRequest request) {
        period.setStartTime(request.startTime());
        period.setEndTime(request.endTime());
        period.setNotes(request.notes());
    }

    private OffsetDateTime timestamp(Meal meal) {
        return meal.getMealDate().atTime(meal.getMealTime()).atZone(DateTimes.USER_ZONE).toOffsetDateTime();
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
