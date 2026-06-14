package com.jllado.weightcontrol.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.AuthenticatedUser;
import com.jllado.weightcontrol.security.JwtSessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtSessionService jwtSessionService;
    private final AppProperties properties;
    private final GoogleIdTokenVerifier verifier;

    public AuthService(UserRepository userRepository, JwtSessionService jwtSessionService, AppProperties properties) {
        this.userRepository = userRepository;
        this.jwtSessionService = jwtSessionService;
        this.properties = properties;
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singleton(properties.auth().googleClientId()))
            .build();
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
        ResponseCookie cookie = ResponseCookie.from("wc_session", token)
            .httpOnly(true)
            .secure(properties.auth().secureCookie())
            .sameSite("Lax")
            .path("/")
            .maxAge(properties.auth().jwtExpiration())
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return user;
    }

    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("wc_session", "")
            .httpOnly(true)
            .secure(properties.auth().secureCookie())
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
