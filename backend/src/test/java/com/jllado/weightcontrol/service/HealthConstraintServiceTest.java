package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.CoachHealthConstraintRequest;
import com.jllado.weightcontrol.api.dto.HealthConstraintDtos.HealthConstraintRequest;
import com.jllado.weightcontrol.domain.HealthConstraint;
import com.jllado.weightcontrol.domain.HealthConstraintSource;
import com.jllado.weightcontrol.domain.HealthConstraintType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.HealthConstraintRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthConstraintServiceTest {

    @Mock
    private HealthConstraintRepository repository;

    @InjectMocks
    private HealthConstraintService service;

    @Test
    void createStoresConstraintAndPreservesClinicianSource() {
        User user = user(1L);
        HealthConstraintRequest request = request(
            LocalDate.of(2026, 8, 1),
            null,
            HealthConstraintSource.PHYSIOTHERAPIST,
            true
        );

        service.create(user, request);

        ArgumentCaptor<HealthConstraint> captor = ArgumentCaptor.forClass(HealthConstraint.class);
        verify(repository).save(captor.capture());
        HealthConstraint saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals(HealthConstraintType.CLINICIAN_GUIDANCE, saved.getType());
        assertEquals("Prescribed core exercises", saved.getTitle());
        assertEquals("Bird dogs and side planks three times per week", saved.getDetails());
        assertEquals(HealthConstraintSource.PHYSIOTHERAPIST, saved.getSource());
        assertEquals(request.startDate(), saved.getStartDate());
        assertEquals(request.endDate(), saved.getEndDate());
        assertEquals(request.active(), saved.isActive());
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        HealthConstraintRequest request = request(
            LocalDate.of(2026, 8, 10),
            LocalDate.of(2026, 8, 9),
            HealthConstraintSource.SELF_REPORTED,
            true
        );

        assertThrows(BadRequestException.class, () -> service.create(user(1L), request));
    }

    @Test
    void updateRejectsConstraintOwnedByAnotherUser() {
        User user = user(1L);
        HealthConstraint constraint = constraint(user(2L));
        when(repository.findById(10L)).thenReturn(Optional.of(constraint));

        assertThrows(NotFoundException.class, () -> service.update(
            user,
            10L,
            request(LocalDate.of(2026, 8, 1), null, HealthConstraintSource.DOCTOR, true)
        ));
    }

    @Test
    void deleteRejectsConstraintOwnedByAnotherUser() {
        User user = user(1L);
        when(repository.findById(10L)).thenReturn(Optional.of(constraint(user(2L))));

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
    }

    @Test
    void coachWritesRequireConfirmation() {
        CoachHealthConstraintRequest request = coachRequest(false);

        assertThrows(BadRequestException.class, () -> service.createConfirmed(user(1L), request));
        assertThrows(BadRequestException.class, () -> service.updateConfirmed(user(1L), 10L, request));
    }

    @Test
    void confirmedCoachCreateStoresConstraint() {
        User user = user(1L);

        service.createConfirmed(user, coachRequest(true));

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(constraint ->
            constraint.getUser() == user && constraint.getSource() == HealthConstraintSource.PHYSIOTHERAPIST
        ));
    }

    @Test
    void activeRangeRetrievalUsesInclusiveRepositoryQuery() {
        User user = user(1L);
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 20);
        List<HealthConstraint> constraints = List.of(constraint(user));
        when(repository.findActiveOverlapping(user, from, to)).thenReturn(constraints);

        assertEquals(constraints, service.findActiveOverlapping(user, from, to));
    }

    private HealthConstraintRequest request(
        LocalDate startDate,
        LocalDate endDate,
        HealthConstraintSource source,
        boolean active
    ) {
        return new HealthConstraintRequest(
            HealthConstraintType.CLINICIAN_GUIDANCE,
            "Prescribed core exercises",
            "Bird dogs and side planks three times per week",
            source,
            startDate,
            endDate,
            active
        );
    }

    private CoachHealthConstraintRequest coachRequest(boolean confirmed) {
        return new CoachHealthConstraintRequest(
            HealthConstraintType.CLINICIAN_GUIDANCE,
            "Prescribed core exercises",
            "Bird dogs and side planks three times per week",
            HealthConstraintSource.PHYSIOTHERAPIST,
            LocalDate.of(2026, 8, 1),
            null,
            true,
            confirmed
        );
    }

    private HealthConstraint constraint(User user) {
        HealthConstraint constraint = new HealthConstraint();
        constraint.setId(10L);
        constraint.setUser(user);
        return constraint;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
