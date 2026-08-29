package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class SleepService {

    private final SleepRepository repository;

    public SleepService(SleepRepository repository) {
        this.repository = repository;
    }

    public List<Sleep> findAll(User user) {
        return repository.findByUserOrderBySleepDateDesc(user);
    }

    public List<Sleep> findBetween(User user, LocalDate from, LocalDate to) {
        return repository.findByUserAndSleepDateBetweenOrderBySleepDateAsc(user, from, to);
    }

    public Sleep create(User user, SleepRequest request) {
        validate(request);
        repository.findByUserAndSleepDate(user, request.sleepDate()).ifPresent(existing -> {
            throw new BadRequestException("Sleep entry already exists for this date");
        });
        Sleep sleep = new Sleep();
        sleep.setUser(user);
        apply(sleep, request);
        return repository.save(sleep);
    }

    public Sleep update(User user, Long id, SleepRequest request) {
        validate(request);
        Sleep sleep = requireOwned(user, id);
        repository.findByUserAndSleepDate(user, request.sleepDate())
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BadRequestException("Sleep entry already exists for this date");
            });
        apply(sleep, request);
        return repository.save(sleep);
    }

    public void delete(User user, Long id) {
        repository.delete(requireOwned(user, id));
    }

    public Sleep requireOwned(User user, Long id) {
        Sleep sleep = repository.findById(id).orElseThrow(() -> new NotFoundException("Sleep entry not found"));
        if (!sleep.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Sleep entry not found");
        }
        return sleep;
    }

    private void apply(Sleep sleep, SleepRequest request) {
        sleep.setSleepDate(request.sleepDate());
        sleep.setBedtimeStart(request.bedtimeStart());
        sleep.setBedtimeEnd(request.bedtimeEnd());
        sleep.setTotalSleepDuration(request.totalSleepDuration());
        sleep.setDeepSleepDuration(request.deepSleepDuration());
        sleep.setRemSleepDuration(request.remSleepDuration());
        sleep.setLightSleepDuration(request.lightSleepDuration());
        sleep.setAwakeTime(request.awakeTime());
        sleep.setAverageHeartRate(request.averageHeartRate().setScale(2, RoundingMode.HALF_UP));
        sleep.setAverageHrv(request.averageHrv());
    }

    private void validate(SleepRequest request) {
        if (request.sleepDate().isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Sleep date cannot be in the future");
        }
        if (!request.bedtimeStart().isBefore(request.bedtimeEnd())) {
            throw new BadRequestException("Bedtime start must be before bedtime end");
        }
        validateNonNegative(request.averageHeartRate(), "Average heart rate");
    }

    private void validateNonNegative(BigDecimal value, String name) {
        if (value.signum() < 0) {
            throw new BadRequestException(name + " cannot be negative");
        }
    }
}
