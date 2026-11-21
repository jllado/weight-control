package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.SleepDtos.HeartRateLimitSetting;
import com.jllado.weightcontrol.domain.OvernightHeartRateLimit;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.OvernightHeartRateLimitRepository;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.service.OvernightHeartRateAnalysis.Limit;
import com.jllado.weightcontrol.service.OvernightHeartRateAnalysis.Night;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OvernightHeartRateLimitService {
    private final OvernightHeartRateLimitRepository limits;
    private final SleepRepository sleeps;
    private final UserRepository users;

    public OvernightHeartRateLimitService(OvernightHeartRateLimitRepository limits, SleepRepository sleeps, UserRepository users) {
        this.limits = limits;
        this.sleeps = sleeps;
        this.users = users;
    }

    public List<Limit> history(User user) {
        return limits.findByUserOrderByEffectiveFromAsc(user).stream()
            .map(limit -> new Limit(limit.getEffectiveFrom(), limit.getBpm())).toList();
    }

    public HeartRateLimitSetting current(User user) {
        return limits.findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, LocalDate.now(DateTimes.USER_ZONE))
            .map(HeartRateLimitSetting::from).orElseGet(() -> new HeartRateLimitSetting(null, null));
    }

    // SleepService holds the user lock before changing sleep evidence.
    @Transactional
    public void initializeIfEligible(User user) {
        if (limits.existsByUser(user)) return;
        var nights = sleeps.findFirst30ByUserAndAverageHeartRateGreaterThanOrderBySleepDateAsc(user, BigDecimal.ZERO).stream()
            .map(sleep -> new Night(sleep.getSleepDate(), sleep.getAverageHeartRate())).toList();
        if (nights.size() == 30) {
            var bpm = OvernightHeartRateAnalysis.median(nights).add(BigDecimal.valueOf(5));
            limits.save(new OvernightHeartRateLimit(user, nights.getLast().date(), bpm));
        }
    }

    @Transactional
    public HeartRateLimitSetting update(User user, BigDecimal bpm) {
        users.findByIdForUpdate(user.getId());
        var today = LocalDate.now(DateTimes.USER_ZONE);
        var current = limits.findFirstByUserAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(user, today)
            .orElseThrow(() -> new BadRequestException("The personal alert threshold is available after 30 recorded nights with a heart rate."));
        if (current.getBpm().compareTo(bpm) == 0) return HeartRateLimitSetting.from(current);
        if (current.getEffectiveFrom().equals(today)) {
            current.setBpm(bpm);
            return HeartRateLimitSetting.from(current);
        }
        return HeartRateLimitSetting.from(limits.save(new OvernightHeartRateLimit(user, today, bpm)));
    }
}
