package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.api.dto.UserProfileDtos.UserProfileRequest;
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
            false
        );
        when(userRepository.save(user)).thenReturn(user);

        User updated = service.update(user, request);

        assertEquals(LocalDate.of(1979, 4, 28), updated.getBirthDate());
        assertEquals(175, updated.getHeightCm());
        assertEquals(UserSex.MALE, updated.getSex());
        assertEquals(UserFitnessLevel.ACTIVE, updated.getFitnessLevel());
        assertEquals(false, updated.isTakesMedication());
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
            false
        );

        assertThrows(BadRequestException.class, () -> service.update(user, request));
    }
}
