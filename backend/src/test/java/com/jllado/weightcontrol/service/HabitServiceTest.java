package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.domain.Habit;
import com.jllado.weightcontrol.domain.HabitBaseline;
import com.jllado.weightcontrol.domain.HabitCheckin;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.HabitBaselineRepository;
import com.jllado.weightcontrol.repository.HabitCheckinRepository;
import com.jllado.weightcontrol.repository.HabitRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HabitServiceTest {

    @Mock
    private HabitRepository repository;
    @Mock
    private HabitBaselineRepository baselineRepository;
    @Mock
    private HabitCheckinRepository checkinRepository;

    private HabitService service;
    private User user;
    private Habit habit;
    private HabitBaseline baseline;
    private List<HabitCheckin> storedCheckins;

    @BeforeEach
    void setUp() {
        service = new HabitService(repository, baselineRepository, checkinRepository);
        user = new User();
        user.setId(1L);
        habit = new Habit();
        habit.setId(10L);
        habit.setUser(user);
        habit.setTimes(7);
        habit.setCurrentStrike(2);
        habit.setBestStrike(3);
        baseline = new HabitBaseline();
        baseline.setHabit(habit);
        baseline.setCompletionTotal(5);
        baseline.setCurrentStreak(2);
        baseline.setBestStreak(3);
        baseline.setLastDate(LocalDate.parse("2026-08-10"));
        storedCheckins = new ArrayList<>(List.of(checkin(20L, "2026-08-12"), checkin(21L, "2026-08-13")));
        when(repository.findByIdForUpdate(habit.getId())).thenReturn(Optional.of(habit));
        when(repository.save(habit)).thenReturn(habit);
        when(baselineRepository.findByHabit(habit)).thenReturn(Optional.of(baseline));
        when(checkinRepository.findByHabitOrderByCheckinDateAscIdAsc(habit)).thenAnswer(ignored -> storedCheckins.stream().sorted(Comparator.comparing(HabitCheckin::getCheckinDate)).toList());
        when(checkinRepository.save(any(HabitCheckin.class))).thenAnswer(invocation -> {
            HabitCheckin checkin = invocation.getArgument(0);
            checkin.setId(22L);
            storedCheckins.add(checkin);
            return checkin;
        });
    }

    @Test
    void backdatedCompletionAndUndoRebuildFromTheLegacyBaseline() {
        LocalDate missingDate = LocalDate.parse("2026-08-11");
        when(checkinRepository.existsByHabitAndCheckinDate(habit, missingDate)).thenReturn(false);

        Habit completed = service.complete(user, habit.getId(), missingDate);

        assertEquals(8, completed.getTimes());
        assertEquals(5, completed.getCurrentStrike());
        assertEquals(5, completed.getBestStrike());
        assertEquals(LocalDate.parse("2026-08-13"), DateTimes.toLocalDate(completed.getLastTimeDate()));

        HabitCheckin added = storedCheckins.stream().filter(checkin -> checkin.getCheckinDate().equals(missingDate)).findFirst().orElseThrow();
        when(checkinRepository.findByHabitAndCheckinDate(habit, missingDate)).thenReturn(Optional.of(added));
        org.mockito.Mockito.doAnswer(ignored -> storedCheckins.remove(added)).when(checkinRepository).delete(added);

        Habit corrected = service.undoCompletion(user, habit.getId(), missingDate);

        assertEquals(7, corrected.getTimes());
        assertEquals(2, corrected.getCurrentStrike());
        assertEquals(3, corrected.getBestStrike());
    }

    @Test
    void completionDoesNotExposeAnotherUsersHabit() {
        User otherUser = new User();
        otherUser.setId(2L);

        assertThrows(NotFoundException.class, () -> service.complete(otherUser, habit.getId(), LocalDate.parse("2026-08-14")));
    }

    private HabitCheckin checkin(Long id, String date) {
        HabitCheckin checkin = new HabitCheckin();
        checkin.setId(id);
        checkin.setHabit(habit);
        checkin.setCheckinDate(LocalDate.parse(date));
        return checkin;
    }
}
