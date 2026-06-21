package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.SleepDtos.SleepRequest;
import com.jllado.weightcontrol.domain.Sleep;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.SleepRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SleepServiceTest {

    @Mock
    private SleepRepository repository;

    @InjectMocks
    private SleepService service;

    @Test
    void createStoresSleep() {
        User user = new User();
        user.setId(1L);
        SleepRequest request = requestFor(LocalDate.now(DateTimes.USER_ZONE));
        when(repository.findByUserAndSleepDate(user, request.sleepDate())).thenReturn(Optional.empty());

        service.create(user, request);

        ArgumentCaptor<Sleep> sleepCaptor = ArgumentCaptor.forClass(Sleep.class);
        verify(repository).save(sleepCaptor.capture());
        assertEquals(request.sleepDate(), sleepCaptor.getValue().getSleepDate());
        assertEquals(request.bedtimeStart(), sleepCaptor.getValue().getBedtimeStart());
        assertEquals(request.bedtimeEnd(), sleepCaptor.getValue().getBedtimeEnd());
        assertEquals(request.totalSleepDuration(), sleepCaptor.getValue().getTotalSleepDuration());
        assertEquals(request.deepSleepDuration(), sleepCaptor.getValue().getDeepSleepDuration());
        assertEquals(request.remSleepDuration(), sleepCaptor.getValue().getRemSleepDuration());
        assertEquals(request.lightSleepDuration(), sleepCaptor.getValue().getLightSleepDuration());
        assertEquals(request.awakeTime(), sleepCaptor.getValue().getAwakeTime());
        assertEquals(request.averageHeartRate().setScale(2), sleepCaptor.getValue().getAverageHeartRate());
        assertEquals(request.averageHrv(), sleepCaptor.getValue().getAverageHrv());
    }

    @Test
    void createRejectsDuplicateDate() {
        User user = new User();
        user.setId(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findByUserAndSleepDate(user, date)).thenReturn(Optional.of(new Sleep()));

        assertThrows(BadRequestException.class, () -> service.create(user, requestFor(date)));
    }

    @Test
    void createRejectsFutureDate() {
        User user = new User();
        user.setId(1L);

        assertThrows(BadRequestException.class, () -> service.create(user, requestFor(LocalDate.now(DateTimes.USER_ZONE).plusDays(1))));
    }

    @Test
    void createRejectsInvalidBedtimeWindow() {
        User user = new User();
        user.setId(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        OffsetDateTime bedtime = OffsetDateTime.now(DateTimes.USER_ZONE).withNano(0);

        assertThrows(BadRequestException.class, () -> service.create(
            user,
            new SleepRequest(date, bedtime, bedtime, 25200, 5400, 7200, 12600, 1800, BigDecimal.valueOf(52.5), 48)
        ));
    }

    @Test
    void updateRejectsDuplicateDateOwnedByAnotherSleep() {
        User user = new User();
        user.setId(1L);
        Sleep sleep = new Sleep();
        sleep.setId(10L);
        sleep.setUser(user);
        Sleep duplicate = new Sleep();
        duplicate.setId(11L);
        duplicate.setUser(user);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findById(10L)).thenReturn(Optional.of(sleep));
        when(repository.findByUserAndSleepDate(user, date)).thenReturn(Optional.of(duplicate));

        assertThrows(BadRequestException.class, () -> service.update(user, 10L, requestFor(date)));
    }

    @Test
    void deleteRejectsForeignSleep() {
        User user = new User();
        user.setId(1L);
        User foreignUser = new User();
        foreignUser.setId(2L);
        Sleep sleep = new Sleep();
        sleep.setId(10L);
        sleep.setUser(foreignUser);
        when(repository.findById(10L)).thenReturn(Optional.of(sleep));

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
    }

    private SleepRequest requestFor(LocalDate sleepDate) {
        OffsetDateTime bedtimeStart = sleepDate.minusDays(1).atTime(23, 0).atOffset(DateTimes.USER_ZONE.getRules().getOffset(java.time.Instant.now()));
        OffsetDateTime bedtimeEnd = sleepDate.atTime(7, 0).atOffset(DateTimes.USER_ZONE.getRules().getOffset(java.time.Instant.now()));
        return new SleepRequest(
            sleepDate,
            bedtimeStart,
            bedtimeEnd,
            25200,
            5400,
            7200,
            12600,
            1800,
            BigDecimal.valueOf(52.5),
            48
        );
    }
}
