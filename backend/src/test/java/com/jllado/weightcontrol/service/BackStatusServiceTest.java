package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.BackStatusDtos.BackRegionStatus;
import com.jllado.weightcontrol.api.dto.BackStatusDtos.BackStatusRequest;
import com.jllado.weightcontrol.domain.BackStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BackStatusRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackStatusServiceTest {

    @Mock
    private BackStatusRepository repository;

    @InjectMocks
    private BackStatusService service;

    @Test
    void createStoresBackStatus() {
        User user = user(1L);
        BackStatusRequest request = requestFor(LocalDate.now(DateTimes.USER_ZONE));
        when(repository.findByUserAndStatusDate(user, request.date())).thenReturn(Optional.empty());

        service.create(user, request);

        ArgumentCaptor<BackStatus> statusCaptor = ArgumentCaptor.forClass(BackStatus.class);
        verify(repository).save(statusCaptor.capture());
        BackStatus status = statusCaptor.getValue();
        assertEquals(request.date(), status.getStatusDate());
        assertEquals(request.lower().pain(), status.getLowerPain());
        assertEquals(request.lower().stiffness(), status.getLowerStiffness());
        assertEquals(request.lower().activityLimitation(), status.getLowerActivityLimitation());
        assertEquals(request.middle().pain(), status.getMiddlePain());
        assertEquals(request.middle().stiffness(), status.getMiddleStiffness());
        assertEquals(request.middle().activityLimitation(), status.getMiddleActivityLimitation());
        assertEquals(request.upper().pain(), status.getUpperPain());
        assertEquals(request.upper().stiffness(), status.getUpperStiffness());
        assertEquals(request.upper().activityLimitation(), status.getUpperActivityLimitation());
        assertEquals(request.note(), status.getNote());
    }

    @Test
    void createRejectsDuplicateDate() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findByUserAndStatusDate(user, date)).thenReturn(Optional.of(new BackStatus()));

        assertThrows(BadRequestException.class, () -> service.create(user, requestFor(date)));
    }

    @Test
    void createRejectsFutureDate() {
        User user = user(1L);

        assertThrows(BadRequestException.class, () -> service.create(user, requestFor(LocalDate.now(DateTimes.USER_ZONE).plusDays(1))));
    }

    @Test
    void updateRejectsDuplicateDateOwnedByAnotherStatus() {
        User user = user(1L);
        BackStatus status = status(10L, user);
        BackStatus duplicate = status(11L, user);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findById(10L)).thenReturn(Optional.of(status));
        when(repository.findByUserAndStatusDate(user, date)).thenReturn(Optional.of(duplicate));

        assertThrows(BadRequestException.class, () -> service.update(user, 10L, requestFor(date)));
    }

    @Test
    void deleteRejectsForeignStatus() {
        User user = user(1L);
        BackStatus status = status(10L, user(2L));
        when(repository.findById(10L)).thenReturn(Optional.of(status));

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
    }

    private BackStatusRequest requestFor(LocalDate date) {
        return new BackStatusRequest(
            date,
            new BackRegionStatus(1, 2, 3),
            new BackRegionStatus(4, 5, 6),
            new BackRegionStatus(7, 8, 9),
            "Daily check-in"
        );
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private BackStatus status(Long id, User user) {
        BackStatus status = new BackStatus();
        status.setId(id);
        status.setUser(user);
        return status;
    }
}
