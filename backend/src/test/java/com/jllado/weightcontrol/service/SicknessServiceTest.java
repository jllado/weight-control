package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.SicknessDtos.SicknessRequest;
import com.jllado.weightcontrol.domain.Sickness;
import com.jllado.weightcontrol.domain.SicknessSeverity;
import com.jllado.weightcontrol.domain.SicknessType;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.SicknessRepository;
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
class SicknessServiceTest {

    @Mock
    private SicknessRepository repository;

    @InjectMocks
    private SicknessService service;

    @Test
    void createStoresSickness() {
        User user = new User();
        user.setId(1L);
        SicknessRequest request = new SicknessRequest(LocalDate.now(DateTimes.USER_ZONE), SicknessType.COLD, SicknessSeverity.MEDIUM, "Sneezing all day");
        when(repository.findByUserAndSicknessDate(user, request.date())).thenReturn(Optional.empty());

        service.create(user, request);

        ArgumentCaptor<Sickness> sicknessCaptor = ArgumentCaptor.forClass(Sickness.class);
        verify(repository).save(sicknessCaptor.capture());
        assertEquals(request.date(), sicknessCaptor.getValue().getSicknessDate());
        assertEquals(request.type(), sicknessCaptor.getValue().getType());
        assertEquals(request.severity(), sicknessCaptor.getValue().getSeverity());
        assertEquals(request.note(), sicknessCaptor.getValue().getNote());
    }

    @Test
    void createRejectsDuplicateDate() {
        User user = new User();
        user.setId(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findByUserAndSicknessDate(user, date)).thenReturn(Optional.of(new Sickness()));

        assertThrows(BadRequestException.class, () -> service.create(user, new SicknessRequest(date, SicknessType.COLD, SicknessSeverity.LOW, null)));
    }

    @Test
    void createRejectsFutureDate() {
        User user = new User();
        user.setId(1L);

        assertThrows(BadRequestException.class, () -> service.create(user, new SicknessRequest(LocalDate.now(DateTimes.USER_ZONE).plusDays(1), SicknessType.COLD, SicknessSeverity.LOW, null)));
    }

    @Test
    void updateRejectsDuplicateDateOwnedByAnotherSickness() {
        User user = new User();
        user.setId(1L);
        Sickness sickness = new Sickness();
        sickness.setId(10L);
        sickness.setUser(user);
        Sickness duplicate = new Sickness();
        duplicate.setId(11L);
        duplicate.setUser(user);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findById(10L)).thenReturn(Optional.of(sickness));
        when(repository.findByUserAndSicknessDate(user, date)).thenReturn(Optional.of(duplicate));

        assertThrows(BadRequestException.class, () -> service.update(user, 10L, new SicknessRequest(date, SicknessType.FLU, SicknessSeverity.HIGH, "Fever")));
    }

    @Test
    void deleteRejectsForeignSickness() {
        User user = new User();
        user.setId(1L);
        User foreignUser = new User();
        foreignUser.setId(2L);
        Sickness sickness = new Sickness();
        sickness.setId(10L);
        sickness.setUser(foreignUser);
        when(repository.findById(10L)).thenReturn(Optional.of(sickness));

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
    }
}
