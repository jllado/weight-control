package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.jllado.weightcontrol.api.dto.MoodDtos.MoodRequest;
import com.jllado.weightcontrol.domain.Mood;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.MoodRepository;
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
class MoodServiceTest {

    @Mock
    private MoodRepository repository;

    @InjectMocks
    private MoodService service;

    @Test
    void createStoresMood() {
        User user = new User();
        user.setId(1L);
        MoodRequest request = new MoodRequest(LocalDate.now(DateTimes.USER_ZONE), 4, "Felt good");
        when(repository.findByUserAndMoodDate(user, request.date())).thenReturn(Optional.empty());

        service.create(user, request);

        ArgumentCaptor<Mood> moodCaptor = ArgumentCaptor.forClass(Mood.class);
        verify(repository).save(moodCaptor.capture());
        assertEquals(request.date(), moodCaptor.getValue().getMoodDate());
        assertEquals(request.value(), moodCaptor.getValue().getValue());
        assertEquals(request.note(), moodCaptor.getValue().getNote());
    }

    @Test
    void createRejectsDuplicateDate() {
        User user = new User();
        user.setId(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findByUserAndMoodDate(user, date)).thenReturn(Optional.of(new Mood()));

        assertThrows(BadRequestException.class, () -> service.create(user, new MoodRequest(date, 3, null)));
    }

    @Test
    void createRejectsFutureDate() {
        User user = new User();
        user.setId(1L);

        assertThrows(BadRequestException.class, () -> service.create(user, new MoodRequest(LocalDate.now(DateTimes.USER_ZONE).plusDays(1), 3, null)));
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
        when(repository.findByUserAndMoodDate(user, date)).thenReturn(Optional.of(duplicate));

        assertThrows(BadRequestException.class, () -> service.update(user, 10L, new MoodRequest(date, 2, "note")));
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
}
