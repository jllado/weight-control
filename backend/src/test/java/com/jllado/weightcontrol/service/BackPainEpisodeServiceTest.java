package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeCreateRequest;
import com.jllado.weightcontrol.api.dto.BackPainEpisodeDtos.BackPainEpisodeUpdateRequest;
import com.jllado.weightcontrol.domain.BackPainEpisode;
import com.jllado.weightcontrol.domain.BackPainSeverity;
import com.jllado.weightcontrol.domain.BackRegion;
import com.jllado.weightcontrol.domain.BackSide;
import com.jllado.weightcontrol.domain.MoodPeriod;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.BackPainEpisodeRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.List;
import com.jllado.weightcontrol.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackPainEpisodeServiceTest {

    @Mock
    private BackPainEpisodeRepository repository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BackPainEpisodeService service;

    @Test
    void createStoresEpisodeWithCurrentTime() {
        User user = user(1L);
        BackPainEpisodeCreateRequest request = createRequest(LocalDate.now(DateTimes.USER_ZONE));

        service.create(user, request);

        ArgumentCaptor<BackPainEpisode> captor = ArgumentCaptor.forClass(BackPainEpisode.class);
        verify(repository).save(captor.capture());
        BackPainEpisode episode = captor.getValue();
        assertEquals(user, episode.getUser());
        assertEquals(request.date(), episode.getEpisodeDate());
        assertNotNull(episode.getEpisodeTime());
        assertEquals(request.period(), episode.getPeriod());
        assertEquals(request.region(), episode.getRegion());
        assertEquals(request.side(), episode.getSide());
        assertEquals(request.severity(), episode.getSeverity());
        assertEquals(request.note(), episode.getNote());
    }

    @Test
    void createRejectsRepeatedLocationInSamePeriod() {
        User user = user(1L);
        BackPainEpisodeCreateRequest request = createRequest(LocalDate.now(DateTimes.USER_ZONE));
        BackPainEpisode existing = episode(10L, user);
        when(repository.findByUserAndEpisodeDateAndPeriodAndRegionAndSide(user, request.date(), request.period(), request.region(), request.side()))
            .thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> service.create(user, request));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(BackPainEpisode.class));
    }

    @Test
    void createAllowsDifferentLocationsInSamePeriod() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        BackPainEpisodeCreateRequest lowerLeft = createRequest(date);
        BackPainEpisodeCreateRequest upperRight = new BackPainEpisodeCreateRequest(date, MoodPeriod.MIDDAY, BackRegion.UPPER, BackSide.RIGHT, BackPainSeverity.SEVERE, null);

        service.create(user, lowerLeft);
        service.create(user, upperRight);

        verify(repository).findByUserAndEpisodeDateAndPeriodAndRegionAndSide(user, date, MoodPeriod.MIDDAY, BackRegion.LOWER, BackSide.LEFT);
        verify(repository).findByUserAndEpisodeDateAndPeriodAndRegionAndSide(user, date, MoodPeriod.MIDDAY, BackRegion.UPPER, BackSide.RIGHT);
    }

    @Test
    void createRejectsFutureDate() {
        User user = user(1L);

        assertThrows(BadRequestException.class, () -> service.create(user, createRequest(LocalDate.now(DateTimes.USER_ZONE).plusDays(1))));
    }

    @Test
    void updatePreservesDateAndTime() {
        User user = user(1L);
        BackPainEpisode episode = episode(10L, user);
        episode.setEpisodeDate(LocalDate.of(2026, 8, 10));
        episode.setEpisodeTime(LocalTime.of(9, 15));
        when(repository.findById(10L)).thenReturn(Optional.of(episode));
        BackPainEpisodeUpdateRequest request = new BackPainEpisodeUpdateRequest(MoodPeriod.EVENING, BackRegion.UPPER, BackSide.RIGHT, BackPainSeverity.SEVERE, "Updated");

        service.update(user, 10L, request);

        assertEquals(LocalDate.of(2026, 8, 10), episode.getEpisodeDate());
        assertEquals(LocalTime.of(9, 15), episode.getEpisodeTime());
        assertEquals(request.period(), episode.getPeriod());
        assertEquals(request.region(), episode.getRegion());
        assertEquals(request.side(), episode.getSide());
        assertEquals(request.severity(), episode.getSeverity());
        assertEquals(request.note(), episode.getNote());
        verify(repository).save(episode);
    }

    @Test
    void updateRejectsAnotherEpisodeAtTheRequestedLocation() {
        User user = user(1L);
        BackPainEpisode episode = episode(10L, user);
        episode.setEpisodeDate(LocalDate.of(2026, 8, 10));
        BackPainEpisode existing = episode(11L, user);
        when(repository.findById(10L)).thenReturn(Optional.of(episode));
        BackPainEpisodeUpdateRequest request = new BackPainEpisodeUpdateRequest(MoodPeriod.MORNING, BackRegion.UPPER, BackSide.RIGHT, BackPainSeverity.SEVERE, null);
        when(repository.findByUserAndEpisodeDateAndPeriodAndRegionAndSide(user, episode.getEpisodeDate(), request.period(), request.region(), request.side()))
            .thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> service.update(user, 10L, request));

        verify(repository, never()).save(episode);
    }

    @Test
    void deleteRejectsForeignEpisode() {
        User user = user(1L);
        BackPainEpisode episode = episode(10L, user(2L));
        when(repository.findById(10L)).thenReturn(Optional.of(episode));

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
    }

    @Test
    void createsPainFreeCheckInWithoutLocation() {
        User user = user(1L);
        service.create(user, new BackPainEpisodeCreateRequest(LocalDate.now(DateTimes.USER_ZONE), MoodPeriod.MORNING, null, null, BackPainSeverity.NONE, "Feeling fine"));
        ArgumentCaptor<BackPainEpisode> captor = ArgumentCaptor.forClass(BackPainEpisode.class);
        verify(repository).save(captor.capture());
        assertEquals(BackPainSeverity.NONE, captor.getValue().getSeverity());
        assertNull(captor.getValue().getRegion());
        assertNull(captor.getValue().getSide());
        verify(userRepository).findByIdForUpdate(user.getId());
    }

    @Test
    void rejectsPainFreeDuplicatesAndConflictsInBothDirections() {
        User user = user(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        BackPainEpisode existing = episode(10L, user);
        when(repository.findByUserAndEpisodeDateAndPeriod(user, date, MoodPeriod.MIDDAY)).thenReturn(List.of(existing));
        for (BackPainSeverity existingSeverity : List.of(BackPainSeverity.NONE, BackPainSeverity.MILD)) {
            existing.setSeverity(existingSeverity);
            assertThrows(BadRequestException.class, () -> service.create(user, new BackPainEpisodeCreateRequest(date, MoodPeriod.MIDDAY, null, null, BackPainSeverity.NONE, null)));
        }
        existing.setSeverity(BackPainSeverity.NONE);
        assertThrows(BadRequestException.class, () -> service.create(user, createRequest(date)));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(BackPainEpisode.class));
    }

    @Test
    void editsBetweenPainAndNoPainAndDeletesCheckIn() {
        User user = user(1L);
        BackPainEpisode existing = episode(10L, user);
        existing.setEpisodeDate(LocalDate.now(DateTimes.USER_ZONE));
        when(repository.findById(10L)).thenReturn(Optional.of(existing));
        when(repository.findByUserAndEpisodeDateAndPeriod(user, existing.getEpisodeDate(), MoodPeriod.MIDDAY)).thenReturn(List.of(existing));
        service.update(user, 10L, new BackPainEpisodeUpdateRequest(MoodPeriod.MIDDAY, null, null, BackPainSeverity.NONE, null));
        assertEquals(BackPainSeverity.NONE, existing.getSeverity());
        assertNull(existing.getRegion());
        service.update(user, 10L, new BackPainEpisodeUpdateRequest(MoodPeriod.MIDDAY, BackRegion.LOWER, BackSide.LEFT, BackPainSeverity.MILD, null));
        assertEquals(BackPainSeverity.MILD, existing.getSeverity());
        assertEquals(BackSide.LEFT, existing.getSide());
        service.delete(user, 10L);
        verify(repository).delete(existing);
    }

    @Test
    void rejectsConflictingEditsWithoutChangingSavedState() {
        User user = user(1L);
        BackPainEpisode existing = episode(10L, user);
        existing.setEpisodeDate(LocalDate.now(DateTimes.USER_ZONE));
        existing.setSeverity(BackPainSeverity.MILD);
        BackPainEpisode other = episode(11L, user);
        other.setSeverity(BackPainSeverity.MODERATE);
        when(repository.findById(10L)).thenReturn(Optional.of(existing));
        when(repository.findByUserAndEpisodeDateAndPeriod(user, existing.getEpisodeDate(), MoodPeriod.MIDDAY)).thenReturn(List.of(existing, other));
        assertThrows(BadRequestException.class, () -> service.update(user, 10L, new BackPainEpisodeUpdateRequest(MoodPeriod.MIDDAY, null, null, BackPainSeverity.NONE, null)));
        other.setSeverity(BackPainSeverity.NONE);
        assertThrows(BadRequestException.class, () -> service.update(user, 10L, new BackPainEpisodeUpdateRequest(MoodPeriod.MIDDAY, BackRegion.LOWER, BackSide.LEFT, BackPainSeverity.MILD, null)));
        assertEquals(BackPainSeverity.MILD, existing.getSeverity());
        verify(repository, never()).save(existing);
    }

    private BackPainEpisodeCreateRequest createRequest(LocalDate date) {
        return new BackPainEpisodeCreateRequest(date, MoodPeriod.MIDDAY, BackRegion.LOWER, BackSide.LEFT, BackPainSeverity.MODERATE, "Daily episode");
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private BackPainEpisode episode(Long id, User user) {
        BackPainEpisode episode = new BackPainEpisode();
        episode.setId(id);
        episode.setUser(user);
        return episode;
    }
}
