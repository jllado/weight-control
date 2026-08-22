package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.UserProfileDtos.UserProfileRequest;
import com.jllado.weightcontrol.api.dto.UserProfileDtos.UserProfileResponse;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.UserProfileService;
import com.jllado.weightcontrol.service.PersonalRecordService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final CurrentUserService currentUserService;
    private final UserProfileService userProfileService;
    private final PersonalRecordService personalRecordService;

    public UserProfileController(CurrentUserService currentUserService, UserProfileService userProfileService, PersonalRecordService personalRecordService) {
        this.currentUserService = currentUserService;
        this.userProfileService = userProfileService;
        this.personalRecordService = personalRecordService;
    }

    @GetMapping
    public UserProfileResponse get() {
        return UserProfileResponse.from(currentUserService.requireUser());
    }

    @PutMapping
    public UserProfileResponse update(@Valid @RequestBody UserProfileRequest request) {
        User user = currentUserService.requireUser();
        User updated = userProfileService.update(user, request);
        personalRecordService.rebuild(updated);
        return UserProfileResponse.from(updated);
    }
}
