package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.api.dto.UserProfileDtos.UserProfileRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.util.DateTimes;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class UserProfileService {

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User update(User user, UserProfileRequest request) {
        validate(request);
        user.setBirthDate(request.birthDate());
        user.setHeightCm(request.heightCm());
        user.setSex(request.sex());
        user.setFitnessLevel(request.fitnessLevel());
        user.setTakesMedication(request.takesMedication());
        return userRepository.save(user);
    }

    private void validate(UserProfileRequest request) {
        if (request.birthDate() != null && request.birthDate().isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Birth date cannot be in the future");
        }
    }
}
