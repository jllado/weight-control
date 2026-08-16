package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.LipidPanelDtos.LipidPanelRequest;
import com.jllado.weightcontrol.domain.LipidPanel;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.LipidPanelRepository;
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
class LipidPanelServiceTest {

    @Mock
    private LipidPanelRepository repository;

    @InjectMocks
    private LipidPanelService service;

    @Test
    void createStoresCompletePanel() {
        User user = user(1L);
        LipidPanelRequest request = request(LocalDate.of(2026, 2, 2));
        when(repository.findByUserAndPanelDate(user, request.date())).thenReturn(Optional.empty());

        service.create(user, request);

        ArgumentCaptor<LipidPanel> captor = ArgumentCaptor.forClass(LipidPanel.class);
        verify(repository).save(captor.capture());
        LipidPanel panel = captor.getValue();
        assertEquals(user, panel.getUser());
        assertEquals(request.date(), panel.getPanelDate());
        assertEquals(request.totalCholesterol(), panel.getTotalCholesterol());
        assertEquals(request.hdlCholesterol(), panel.getHdlCholesterol());
        assertEquals(request.ldlCholesterol(), panel.getLdlCholesterol());
        assertEquals(request.triglycerides(), panel.getTriglycerides());
    }

    @Test
    void createRejectsDuplicateDate() {
        User user = user(1L);
        LipidPanelRequest request = request(LocalDate.of(2026, 2, 2));
        when(repository.findByUserAndPanelDate(user, request.date())).thenReturn(Optional.of(panel(10L, user)));

        assertThrows(BadRequestException.class, () -> service.create(user, request));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(LipidPanel.class));
    }

    @Test
    void createRejectsFutureDate() {
        User user = user(1L);

        assertThrows(BadRequestException.class, () -> service.create(
            user,
            request(LocalDate.now(DateTimes.USER_ZONE).plusDays(1))
        ));
    }

    @Test
    void updateRejectsDuplicateDateOwnedByAnotherPanel() {
        User user = user(1L);
        LipidPanel panel = panel(10L, user);
        LipidPanel duplicate = panel(11L, user);
        LocalDate date = LocalDate.of(2026, 2, 2);
        when(repository.findById(10L)).thenReturn(Optional.of(panel));
        when(repository.findByUserAndPanelDate(user, date)).thenReturn(Optional.of(duplicate));

        assertThrows(BadRequestException.class, () -> service.update(user, 10L, request(date)));

        verify(repository, never()).save(panel);
    }

    @Test
    void updateAllowsKeepingTheSameDate() {
        User user = user(1L);
        LipidPanel panel = panel(10L, user);
        LocalDate date = LocalDate.of(2026, 2, 2);
        when(repository.findById(10L)).thenReturn(Optional.of(panel));
        when(repository.findByUserAndPanelDate(user, date)).thenReturn(Optional.of(panel));

        service.update(user, 10L, request(date));

        verify(repository).save(panel);
        assertEquals(211, panel.getTotalCholesterol());
    }

    @Test
    void deleteRejectsForeignPanel() {
        User user = user(1L);
        when(repository.findById(10L)).thenReturn(Optional.of(panel(10L, user(2L))));

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
    }

    private LipidPanelRequest request(LocalDate date) {
        return new LipidPanelRequest(date, 211, 63, 133, 77);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private LipidPanel panel(Long id, User user) {
        LipidPanel panel = new LipidPanel();
        panel.setId(id);
        panel.setUser(user);
        return panel;
    }
}
