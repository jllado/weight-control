package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachCoachingPlanRequest;
import com.jllado.weightcontrol.api.dto.CoachingPlanDtos.CoachingPlanRequest;
import com.jllado.weightcontrol.domain.CoachingPlan;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.CoachingPlanRepository;
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
class CoachingPlanServiceTest {

    @Mock
    private CoachingPlanRepository repository;

    @InjectMocks
    private CoachingPlanService service;

    @Test
    void firstReplacementCreatesTheUsersCompletePlan() {
        User user = user(1L);
        CoachingPlanRequest request = request(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10));

        service.replace(user, request);

        ArgumentCaptor<CoachingPlan> captor = ArgumentCaptor.forClass(CoachingPlan.class);
        verify(repository).save(captor.capture());
        CoachingPlan saved = captor.getValue();
        assertSame(user, saved.getUser());
        assertEquals(request.goal(), saved.getGoal());
        assertEquals(request.principles(), saved.getPrinciples());
        assertEquals(request.priorities(), saved.getPriorities());
        assertEquals(request.actions(), saved.getActions());
        assertEquals(request.startDate(), saved.getStartDate());
        assertEquals(request.reviewDate(), saved.getReviewDate());
        assertEquals(request.notes(), saved.getNotes());
    }

    @Test
    void laterReplacementAtomicallyReplacesEveryMutableField() {
        User user = user(1L);
        CoachingPlan existing = new CoachingPlan();
        existing.setId(20L);
        existing.setUser(user);
        existing.setGoal("Old goal");
        existing.setPrinciples(List.of("Old principle"));
        existing.setPriorities(List.of("Old priority"));
        existing.setActions(List.of("Old action"));
        existing.setStartDate(LocalDate.of(2026, 7, 1));
        existing.setReviewDate(LocalDate.of(2026, 8, 1));
        existing.setNotes("Old notes");
        CoachingPlanRequest request = request(LocalDate.of(2026, 8, 10), null);
        when(repository.findByUser(user)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        CoachingPlan saved = service.replace(user, request);

        assertSame(existing, saved);
        assertEquals(request.goal(), saved.getGoal());
        assertEquals(request.principles(), saved.getPrinciples());
        assertEquals(request.priorities(), saved.getPriorities());
        assertEquals(request.actions(), saved.getActions());
        assertEquals(request.startDate(), saved.getStartDate());
        assertEquals(request.reviewDate(), saved.getReviewDate());
        assertEquals(request.notes(), saved.getNotes());
    }

    @Test
    void readsAreScopedToTheExactUser() {
        User user = user(1L);

        service.find(user);

        verify(repository).findByUser(user);
    }

    @Test
    void replacementRejectsReviewDateBeforeStartDate() {
        CoachingPlanRequest request = request(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 9));

        assertThrows(BadRequestException.class, () -> service.replace(user(1L), request));
    }

    @Test
    void coachReplacementRequiresConfirmation() {
        CoachCoachingPlanRequest request = coachRequest(false);

        assertThrows(BadRequestException.class, () -> service.replaceConfirmed(user(1L), request));
    }

    @Test
    void confirmedCoachReplacementStoresTheCompletePlan() {
        User user = user(1L);

        service.replaceConfirmed(user, coachRequest(true));

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(plan ->
            plan.getUser() == user
                && plan.getGoal().equals("Improve strength consistently")
                && plan.getActions().equals(List.of("Complete three strength sessions"))
        ));
    }

    private CoachingPlanRequest request(LocalDate startDate, LocalDate reviewDate) {
        return new CoachingPlanRequest(
            "Improve strength consistently",
            List.of("Train without aggravating pain"),
            List.of("Consistency", "Recovery"),
            List.of("Complete three strength sessions"),
            startDate,
            reviewDate,
            "Review training tolerance"
        );
    }

    private CoachCoachingPlanRequest coachRequest(boolean confirmed) {
        CoachingPlanRequest request = request(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10));
        return new CoachCoachingPlanRequest(
            request.goal(),
            request.principles(),
            request.priorities(),
            request.actions(),
            request.startDate(),
            request.reviewDate(),
            request.notes(),
            confirmed
        );
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
