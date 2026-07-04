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
