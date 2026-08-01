package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.UserProfileDtos.CalorieShortcutsRequest;
import com.jllado.weightcontrol.api.dto.UserProfileDtos.TypicalCaloriesPerDayRequest;
import com.jllado.weightcontrol.api.dto.UserProfileDtos.UserProfileRequest;
import com.jllado.weightcontrol.api.dto.UserProfileDtos.UserProfileResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.domain.UserFitnessLevel;
import com.jllado.weightcontrol.domain.UserSex;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserProfileService service;

    @Test
    void updateStoresProfileFields() {
        User user = new User();
        UserProfileRequest request = new UserProfileRequest(
            LocalDate.of(1979, 4, 28),
            175,
            UserSex.MALE,
            UserFitnessLevel.ACTIVE,
            false,
            2500,
            new TypicalCaloriesPerDayRequest(2983, 2983, 1853, 1853, 1853, 1853, 1122),
            new CalorieShortcutsRequest(1850, 3000, 4000, 5000)
        );
        when(userRepository.save(user)).thenReturn(user);

        User updated = service.update(user, request);

        assertEquals(LocalDate.of(1979, 4, 28), updated.getBirthDate());
        assertEquals(175, updated.getHeightCm());
        assertEquals(UserSex.MALE, updated.getSex());
        assertEquals(UserFitnessLevel.ACTIVE, updated.getFitnessLevel());
        assertEquals(false, updated.isTakesMedication());
        assertEquals(2500, updated.getWeeklyAverageCalorieMaximum());
        assertEquals(2983, updated.getTypicalCaloriesSaturday());
        assertEquals(2983, updated.getTypicalCaloriesSunday());
        assertEquals(1853, updated.getTypicalCaloriesMonday());
        assertEquals(1853, updated.getTypicalCaloriesTuesday());
        assertEquals(1853, updated.getTypicalCaloriesWednesday());
        assertEquals(1853, updated.getTypicalCaloriesThursday());
        assertEquals(1122, updated.getTypicalCaloriesFriday());
        assertEquals(1850, updated.getCalorieShortcutOnPlan());
        assertEquals(3000, updated.getCalorieShortcutFlexible());
        assertEquals(4000, updated.getCalorieShortcutOffPlan());
        assertEquals(5000, updated.getCalorieShortcutBinge());
        assertEquals(2500, UserProfileResponse.from(updated).weeklyAverageCalorieMaximum());
        assertEquals(1850, UserProfileResponse.from(updated).calorieShortcuts().onPlan());
        verify(userRepository).save(user);
    }

    @Test
    void updateRejectsFutureBirthDate() {
        User user = new User();
        UserProfileRequest request = new UserProfileRequest(
            LocalDate.now(DateTimes.USER_ZONE).plusDays(1),
            175,
            UserSex.MALE,
            UserFitnessLevel.ACTIVE,
            false,
            2500,
            new TypicalCaloriesPerDayRequest(2983, 2983, 1853, 1853, 1853, 1853, 1122),
            new CalorieShortcutsRequest(1850, 3000, 4000, 5000)
        );

        assertThrows(BadRequestException.class, () -> service.update(user, request));
    }
}
