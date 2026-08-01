package com.jllado.weightcontrol.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.AuthenticatedUser;
import com.jllado.weightcontrol.security.JwtSessionService;
import com.jllado.weightcontrol.security.SessionCookieService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String ALLOWED_EMAIL = "jllado@gmail.com";
    private static final int TYPICAL_CALORIES_SATURDAY = 2983;
    private static final int TYPICAL_CALORIES_SUNDAY = 2983;
    private static final int TYPICAL_CALORIES_MONDAY = 1853;
    private static final int TYPICAL_CALORIES_TUESDAY = 1853;
    private static final int TYPICAL_CALORIES_WEDNESDAY = 1853;
    private static final int TYPICAL_CALORIES_THURSDAY = 1853;
    private static final int TYPICAL_CALORIES_FRIDAY = 1122;
    private static final int WEEKLY_AVERAGE_CALORIE_MAXIMUM = 2500;
    private static final int CALORIE_SHORTCUT_ON_PLAN = 1850;
    private static final int CALORIE_SHORTCUT_FLEXIBLE = 3000;
    private static final int CALORIE_SHORTCUT_OFF_PLAN = 4000;
    private static final int CALORIE_SHORTCUT_BINGE = 5000;

    private final UserRepository userRepository;
    private final JwtSessionService jwtSessionService;
    private final SessionCookieService sessionCookieService;
    private final GoogleIdTokenVerifier verifier;

    public AuthService(
        UserRepository userRepository,
        JwtSessionService jwtSessionService,
        SessionCookieService sessionCookieService,
        GoogleIdTokenVerifier verifier
    ) {
        this.userRepository = userRepository;
        this.jwtSessionService = jwtSessionService;
        this.sessionCookieService = sessionCookieService;
        this.verifier = verifier;
    }

    public User loginWithGoogle(String credential, HttpServletResponse response) {
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(credential);
        } catch (GeneralSecurityException | IOException e) {
            throw new BadRequestException("Failed to verify Google token");
        }
        if (idToken == null) {
            throw new BadRequestException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        if (!ALLOWED_EMAIL.equals(email)) {
            throw new BadRequestException("Only jllado@gmail.com can log in");
        }
        String googleSub = payload.getSubject();
        String name = (String) payload.get("name");

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User createdUser = new User();
            createdUser.setEmail(email);
            createdUser.setDisplayName(name);
            createdUser.setDashboardAnchorDate(LocalDate.now(com.jllado.weightcontrol.util.DateTimes.USER_ZONE));
            createdUser.setWeeklyAverageCalorieMaximum(WEEKLY_AVERAGE_CALORIE_MAXIMUM);
            createdUser.setTypicalCaloriesSaturday(TYPICAL_CALORIES_SATURDAY);
            createdUser.setTypicalCaloriesSunday(TYPICAL_CALORIES_SUNDAY);
            createdUser.setTypicalCaloriesMonday(TYPICAL_CALORIES_MONDAY);
            createdUser.setTypicalCaloriesTuesday(TYPICAL_CALORIES_TUESDAY);
            createdUser.setTypicalCaloriesWednesday(TYPICAL_CALORIES_WEDNESDAY);
            createdUser.setTypicalCaloriesThursday(TYPICAL_CALORIES_THURSDAY);
            createdUser.setTypicalCaloriesFriday(TYPICAL_CALORIES_FRIDAY);
            createdUser.setCalorieShortcutOnPlan(CALORIE_SHORTCUT_ON_PLAN);
            createdUser.setCalorieShortcutFlexible(CALORIE_SHORTCUT_FLEXIBLE);
            createdUser.setCalorieShortcutOffPlan(CALORIE_SHORTCUT_OFF_PLAN);
            createdUser.setCalorieShortcutBinge(CALORIE_SHORTCUT_BINGE);
            return createdUser;
        });
        if (user.getGoogleSub() == null) {
            user.setGoogleSub(googleSub);
        } else if (!user.getGoogleSub().equals(googleSub)) {
            throw new BadRequestException("Google account does not match imported user");
        }
        user.setDisplayName(name);
        user = userRepository.save(user);

        String token = jwtSessionService.createToken(new AuthenticatedUser(user.getId(), user.getEmail()));
        sessionCookieService.writeSessionCookie(response, token);
        return user;
    }

    public void logout(HttpServletResponse response) {
        sessionCookieService.clearSessionCookie(response);
    }
}
