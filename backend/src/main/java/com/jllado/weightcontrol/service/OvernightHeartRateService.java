package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.SleepRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OvernightHeartRateService {
    private final SleepRepository sleeps;
    private final OvernightHeartRateLimitService limits;

    public OvernightHeartRateService(SleepRepository sleeps, OvernightHeartRateLimitService limits) {
        this.sleeps = sleeps;
        this.limits = limits;
    }

    @Transactional(readOnly = true)
    public OvernightHeartRateAnalysis.Result analysis(User user, LocalDate through) {
        return OvernightHeartRateAnalysis.evaluate(sleeps.findHeartRateNights(user, through), limits.history(user), through);
    }
}
