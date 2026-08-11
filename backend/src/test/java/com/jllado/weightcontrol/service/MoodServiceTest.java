package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.api.dto.MoodDtos.MoodRequest;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MoodRepository;
import com.jllado.weightcontrol.util.DateTimes;
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
class MoodServiceTest {

    @Mock
    private MoodRepository repository;

    @InjectMocks
    private MoodService service;

    @Test
    void createStoresMood() {
        User user = new User();
        user.setId(1L);
        MoodRequest request = new MoodRequest(LocalDate.now(DateTimes.USER_ZONE), MoodPeriod.MORNING, 4, "Felt good");
        when(repository.findByUserAndMoodDateAndPeriod(user, request.date(), request.period())).thenReturn(Optional.empty());

        service.create(user, request);

        ArgumentCaptor<Mood> moodCaptor = ArgumentCaptor.forClass(Mood.class);
        verify(repository).save(moodCaptor.capture());
        assertEquals(request.date(), moodCaptor.getValue().getMoodDate());
        assertEquals(request.period(), moodCaptor.getValue().getPeriod());
        assertEquals(request.value(), moodCaptor.getValue().getValue());
        assertEquals(request.note(), moodCaptor.getValue().getNote());
    }

    @Test
    void createRejectsDuplicateDateAndPeriod() {
        User user = new User();
        user.setId(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findByUserAndMoodDateAndPeriod(user, date, MoodPeriod.MIDDAY)).thenReturn(Optional.of(new Mood()));

        assertThrows(BadRequestException.class, () -> service.create(user, new MoodRequest(date, MoodPeriod.MIDDAY, 3, null)));
    }

    @Test
    void createRejectsFutureDate() {
        User user = new User();
        user.setId(1L);

        assertThrows(BadRequestException.class, () -> service.create(user, new MoodRequest(LocalDate.now(DateTimes.USER_ZONE).plusDays(1), MoodPeriod.EVENING, 3, null)));
    }

    @Test
    void updateRejectsDuplicateDateOwnedByAnotherMood() {
        User user = new User();
        user.setId(1L);
        Mood mood = new Mood();
        mood.setId(10L);
        mood.setUser(user);
        Mood duplicate = new Mood();
        duplicate.setId(11L);
        duplicate.setUser(user);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findById(10L)).thenReturn(Optional.of(mood));
        when(repository.findByUserAndMoodDateAndPeriod(user, date, MoodPeriod.EVENING)).thenReturn(Optional.of(duplicate));

        assertThrows(BadRequestException.class, () -> service.update(user, 10L, new MoodRequest(date, MoodPeriod.EVENING, 2, "note")));
    }

    @Test
    void averageWeightsEachDateOnce() {
        LocalDate firstDate = LocalDate.of(2026, 8, 10);
        LocalDate secondDate = firstDate.plusDays(1);

        assertEquals(
            0,
            new java.math.BigDecimal("4.00").compareTo(service.average(List.of(
                mood(firstDate, MoodPeriod.MORNING, 1),
                mood(firstDate, MoodPeriod.MIDDAY, 3),
                mood(firstDate, MoodPeriod.EVENING, 5),
                mood(secondDate, MoodPeriod.MORNING, 5)
            )))
        );
    }

    @Test
    void findAllOrdersNewestDatesAndPeriodsChronologically() {
        User user = new User();
        LocalDate newest = LocalDate.of(2026, 8, 11);
        when(repository.findByUserOrderByMoodDateDesc(user)).thenReturn(List.of(
            mood(newest, MoodPeriod.EVENING, 5),
            mood(newest.minusDays(1), MoodPeriod.MORNING, 3),
            mood(newest, MoodPeriod.MORNING, 4),
            mood(newest, MoodPeriod.MIDDAY, 4)
        ));

        List<Mood> moods = service.findAll(user);

        assertEquals(List.of(MoodPeriod.MORNING, MoodPeriod.MIDDAY, MoodPeriod.EVENING), moods.subList(0, 3).stream().map(Mood::getPeriod).toList());
        assertEquals(newest.minusDays(1), moods.get(3).getMoodDate());
    }

    @Test
    void deleteRejectsForeignMood() {
        User user = new User();
        user.setId(1L);
        User foreignUser = new User();
        foreignUser.setId(2L);
        Mood mood = new Mood();
        mood.setId(10L);
        mood.setUser(foreignUser);
        when(repository.findById(10L)).thenReturn(Optional.of(mood));

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
    }

    private Mood mood(LocalDate date, MoodPeriod period, int value) {
        Mood mood = new Mood();
        mood.setMoodDate(date);
        mood.setPeriod(period);
        mood.setValue(value);
        return mood;
    }
}
