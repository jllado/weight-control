package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.ReflectionDtos.WarningReviewEnvelope;
import com.jllado.weightcontrol.domain.CoachWarningStatus;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.CoachWarningRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyWarningReviewServiceTest {
    @Test
    void reviewDateUsesMadridLocalMidnightAcrossAutumnOffsetChange() {
        var user = new User();
        user.setId(1L);
        var repository = mock(CoachWarningRepository.class);
        when(repository.findByUserAndStatusOrderByTypeAsc(user, CoachWarningStatus.ACTIVE)).thenReturn(List.of());
        var entityManager = mock(EntityManager.class);
        var warnings = mock(CoachWarningService.class);
        var first = new DailyWarningReviewService(entityManager, repository, warnings,
            Clock.fixed(Instant.parse("2026-10-24T22:30:00Z"), ZoneOffset.UTC));
        var second = new DailyWarningReviewService(entityManager, repository, warnings,
            Clock.fixed(Instant.parse("2026-10-25T23:30:00Z"), ZoneOffset.UTC));

        assertEquals(LocalDate.of(2026, 10, 25), first.review(user,
            new WarningReviewEnvelope(LocalDate.of(2026, 10, 25), List.of(), List.of())).reviewedDate());
        assertEquals(LocalDate.of(2026, 10, 26), second.review(user,
            new WarningReviewEnvelope(LocalDate.of(2026, 10, 26), List.of(), List.of())).reviewedDate());
        assertThrows(BadRequestException.class, () -> second.review(user,
            new WarningReviewEnvelope(LocalDate.of(2026, 10, 25), List.of(), List.of())));
    }
}
