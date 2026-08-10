package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.RoutineDtos.RoutineRequest;
import com.jllado.weightcontrol.domain.Routine;
import com.jllado.weightcontrol.domain.RoutineCheckin;
import com.jllado.weightcontrol.domain.RoutineType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.RoutineCheckinRepository;
import com.jllado.weightcontrol.repository.RoutineRepository;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

    @Mock
    private RoutineRepository repository;

    @Mock
    private RoutineCheckinRepository checkinRepository;

    @InjectMocks
    private RoutineService service;

    @Test
    void updateStoresTheOptionalReminderAtMinutePrecision() {
        User user = new User();
        user.setId(1L);
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        when(repository.findById(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);

        Routine updated = service.update(
            user,
            routine.getId(),
            new RoutineRequest("Meditation", Set.of(RoutineType.MIND), LocalTime.of(13, 7, 45))
        );

        assertEquals(LocalTime.of(13, 7), updated.getReminderTime());
    }

    @Test
    void updateClearsTheOptionalReminder() {
        User user = new User();
        user.setId(1L);
        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        routine.setReminderTime(LocalTime.of(13, 7));
        when(repository.findById(routine.getId())).thenReturn(Optional.of(routine));
        when(repository.save(routine)).thenReturn(routine);

        Routine updated = service.update(
            user,
            routine.getId(),
            new RoutineRequest("Meditation", Set.of(RoutineType.MIND), null)
        );

        assertNull(updated.getReminderTime());
    }

    @Test
    void undoCheckinRebuildsSummaryFromRemainingCheckins() {
        User user = new User();
        user.setId(1L);

        Routine routine = new Routine();
        routine.setId(2L);
        routine.setUser(user);
        routine.setCurrentStrike(1);
        routine.setBestStrike(2);
        routine.setLastTimeDate(OffsetDateTime.parse("2026-06-12T00:00:00+02:00"));

        OffsetDateTime first = OffsetDateTime.parse("2026-06-10T00:00:00+02:00");
        OffsetDateTime second = OffsetDateTime.parse("2026-06-11T00:00:00+02:00");
        OffsetDateTime removed = OffsetDateTime.parse("2026-06-12T00:00:00+02:00");

        RoutineCheckin removedCheckin = new RoutineCheckin();
        removedCheckin.setRoutine(routine);
        removedCheckin.setCheckedAt(removed);

        RoutineCheckin firstCheckin = new RoutineCheckin();
        firstCheckin.setRoutine(routine);
        firstCheckin.setCheckedAt(first);

        RoutineCheckin secondCheckin = new RoutineCheckin();
        secondCheckin.setRoutine(routine);
        secondCheckin.setCheckedAt(second);

        when(repository.findById(routine.getId())).thenReturn(Optional.of(routine));
        when(checkinRepository.findByRoutineAndCheckedAt(routine, removed)).thenReturn(Optional.of(removedCheckin));
        when(checkinRepository.findByRoutineOrderByCheckedAtAsc(routine)).thenReturn(List.of(firstCheckin, secondCheckin));
        when(repository.save(routine)).thenReturn(routine);

        service.undoCheckin(user, routine.getId(), removed);

        verify(checkinRepository).delete(removedCheckin);
        ArgumentCaptor<Routine> savedRoutine = ArgumentCaptor.forClass(Routine.class);
        verify(repository).save(savedRoutine.capture());
        assertEquals(2, savedRoutine.getValue().getCurrentStrike());
        assertEquals(2, savedRoutine.getValue().getBestStrike());
        assertEquals(second, savedRoutine.getValue().getLastTimeDate());
    }
}
