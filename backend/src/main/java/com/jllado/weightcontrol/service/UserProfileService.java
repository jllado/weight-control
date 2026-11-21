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
    private final PlanningWeekService planningWeeks;

    public UserProfileService(UserRepository userRepository, PlanningWeekService planningWeeks) {
        this.userRepository = userRepository;
        this.planningWeeks = planningWeeks;
    }

    public User update(User authenticatedUser, UserProfileRequest request) {
        validate(request);
        User user = userRepository.findByIdForUpdate(authenticatedUser.getId()).orElseThrow();
        if (request.firstDayOfPlanningWeek() != null) {
            planningWeeks.schedule(user, request.firstDayOfPlanningWeek(), LocalDate.now(DateTimes.USER_ZONE), request.expectedPlanningWeekEffectiveDate());
        }
        user.setBirthDate(request.birthDate());
        user.setHeightCm(request.heightCm());
        if (request.inShapeBodyFatLimit() != null) user.setInShapeBodyFatLimit(request.inShapeBodyFatLimit());
        user.setSex(request.sex());
        user.setFitnessLevel(request.fitnessLevel());
        user.setTakesMedication(request.takesMedication());
        user.setWeeklyAverageCalorieMaximum(request.weeklyAverageCalorieMaximum());
        user.setTypicalCaloriesSaturday(request.typicalCaloriesPerDay().saturday());
        user.setTypicalCaloriesSunday(request.typicalCaloriesPerDay().sunday());
        user.setTypicalCaloriesMonday(request.typicalCaloriesPerDay().monday());
        user.setTypicalCaloriesTuesday(request.typicalCaloriesPerDay().tuesday());
        user.setTypicalCaloriesWednesday(request.typicalCaloriesPerDay().wednesday());
        user.setTypicalCaloriesThursday(request.typicalCaloriesPerDay().thursday());
        user.setTypicalCaloriesFriday(request.typicalCaloriesPerDay().friday());
        user.setCalorieShortcutOnPlan(request.calorieShortcuts().onPlan());
        user.setCalorieShortcutFlexible(request.calorieShortcuts().flexible());
        user.setCalorieShortcutOffPlan(request.calorieShortcuts().offPlan());
        user.setCalorieShortcutBinge(request.calorieShortcuts().binge());
        return user;
    }

    private void validate(UserProfileRequest request) {
        if (request.birthDate() != null && request.birthDate().isAfter(LocalDate.now(DateTimes.USER_ZONE))) {
            throw new BadRequestException("Birth date cannot be in the future");
        }
    }
}
