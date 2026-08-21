package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.CoachFastingPeriodRequest;
import com.jllado.weightcontrol.api.dto.FastingPeriodDtos.FastingPeriodRequest;
import com.jllado.weightcontrol.domain.FastingPeriod;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.FastingPeriodRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FastingPeriodServiceTest {

    @Mock
    private FastingPeriodRepository repository;

    @InjectMocks
    private FastingPeriodService service;

    @Test
    void createStoresCompletedNonOverlappingPeriod() {
        User user = user(1L);
        OffsetDateTime end = OffsetDateTime.now(DateTimes.USER_ZONE).minusHours(1);
        FastingPeriodRequest request = new FastingPeriodRequest(end.minusHours(16), end, "Overnight fast");

        service.create(user, request);

        ArgumentCaptor<FastingPeriod> period = ArgumentCaptor.forClass(FastingPeriod.class);
        verify(repository).save(period.capture());
        assertEquals(request.startTime(), period.getValue().getStartTime());
        assertEquals(request.endTime(), period.getValue().getEndTime());
        assertEquals("Overnight fast", period.getValue().getNotes());
    }

    @Test
    void createRejectsInvalidFutureAndOverlappingPeriods() {
        User user = user(1L);
        OffsetDateTime now = OffsetDateTime.now(DateTimes.USER_ZONE);
        FastingPeriodRequest reversed = new FastingPeriodRequest(now.minusHours(1), now.minusHours(2), null);
        FastingPeriodRequest future = new FastingPeriodRequest(now.minusHours(1), now.plusMinutes(1), null);
        FastingPeriodRequest overlap = new FastingPeriodRequest(now.minusHours(16), now.minusHours(1), null);
        when(repository.existsOverlapping(user, overlap.startTime(), overlap.endTime(), null)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.create(user, reversed));
        assertThrows(BadRequestException.class, () -> service.create(user, future));
        assertThrows(BadRequestException.class, () -> service.create(user, overlap));
    }

    @Test
    void writesRequireOwnershipAndCoachConfirmation() {
        User user = user(1L);
        FastingPeriod foreign = new FastingPeriod();
        foreign.setId(10L);
        foreign.setUser(user(2L));
        when(repository.findById(10L)).thenReturn(Optional.of(foreign));
        OffsetDateTime end = OffsetDateTime.now(DateTimes.USER_ZONE).minusHours(1);
        CoachFastingPeriodRequest request = new CoachFastingPeriodRequest(end.minusHours(16), end, null, false);

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
        assertThrows(BadRequestException.class, () -> service.createConfirmed(user, request));
        assertThrows(BadRequestException.class, () -> service.deleteConfirmed(user, 10L, false));
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
