package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.UrgePause;
import com.jllado.weightcontrol.repository.UrgePauseRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UrgePauseScheduler {
    private final UrgePauseRepository repository;
    private final UrgePauseService service;
    public UrgePauseScheduler(UrgePauseRepository repository, UrgePauseService service) {
        this.repository = repository;
        this.service = service;
    }
    @Scheduled(fixedDelay = 15000)
    public void sendReminders() {
        OffsetDateTime now = OffsetDateTime.now(DateTimes.USER_ZONE);
        repository.findByStatusAndNotifiedAtIsNullAndEndsAtLessThanEqual(UrgePause.Status.ACTIVE, now)
            .forEach(pause -> service.notifyDue(pause.getId(), now));
    }
}
