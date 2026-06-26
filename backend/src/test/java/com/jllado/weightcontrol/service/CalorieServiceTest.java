package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.CalorieDtos.CalorieRequest;
import com.jllado.weightcontrol.domain.Calorie;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.CalorieRepository;
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
class CalorieServiceTest {

    @Mock
    private CalorieRepository repository;

    @InjectMocks
    private CalorieService service;

    @Test
    void createStoresCalories() {
        User user = new User();
        user.setId(1L);
        CalorieRequest request = new CalorieRequest(LocalDate.now(DateTimes.USER_ZONE), 2100);
        when(repository.findByUserAndCalorieDate(user, request.date())).thenReturn(Optional.empty());

        service.create(user, request);

        ArgumentCaptor<Calorie> calorieCaptor = ArgumentCaptor.forClass(Calorie.class);
        verify(repository).save(calorieCaptor.capture());
        assertEquals(request.date(), calorieCaptor.getValue().getCalorieDate());
        assertEquals(request.calories(), calorieCaptor.getValue().getCalories());
    }

    @Test
    void createRejectsDuplicateDate() {
        User user = new User();
        user.setId(1L);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findByUserAndCalorieDate(user, date)).thenReturn(Optional.of(new Calorie()));

        assertThrows(BadRequestException.class, () -> service.create(user, new CalorieRequest(date, 2000)));
    }

    @Test
    void createRejectsFutureDate() {
        User user = new User();
        user.setId(1L);

        assertThrows(BadRequestException.class, () -> service.create(user, new CalorieRequest(LocalDate.now(DateTimes.USER_ZONE).plusDays(1), 2000)));
    }

    @Test
    void updateRejectsDuplicateDateOwnedByAnotherEntry() {
        User user = new User();
        user.setId(1L);
        Calorie calorie = new Calorie();
        calorie.setId(10L);
        calorie.setUser(user);
        Calorie duplicate = new Calorie();
        duplicate.setId(11L);
        duplicate.setUser(user);
        LocalDate date = LocalDate.now(DateTimes.USER_ZONE);
        when(repository.findById(10L)).thenReturn(Optional.of(calorie));
        when(repository.findByUserAndCalorieDate(user, date)).thenReturn(Optional.of(duplicate));

        assertThrows(BadRequestException.class, () -> service.update(user, 10L, new CalorieRequest(date, 2200)));
    }

    @Test
    void deleteRejectsForeignEntry() {
        User user = new User();
        user.setId(1L);
        User foreignUser = new User();
        foreignUser.setId(2L);
        Calorie calorie = new Calorie();
        calorie.setId(10L);
        calorie.setUser(foreignUser);
        when(repository.findById(10L)).thenReturn(Optional.of(calorie));

        assertThrows(NotFoundException.class, () -> service.delete(user, 10L));
    }

    @Test
    void createAllowsZeroCalories() {
        User user = new User();
        user.setId(1L);
        CalorieRequest request = new CalorieRequest(LocalDate.now(DateTimes.USER_ZONE), 0);
        when(repository.findByUserAndCalorieDate(user, request.date())).thenReturn(Optional.empty());

        service.create(user, request);

        ArgumentCaptor<Calorie> calorieCaptor = ArgumentCaptor.forClass(Calorie.class);
        verify(repository).save(calorieCaptor.capture());
        assertEquals(0, calorieCaptor.getValue().getCalories());
    }
}
