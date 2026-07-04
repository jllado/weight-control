package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import com.jllado.weightcontrol.security.AuthenticatedUser;
import com.jllado.weightcontrol.security.JwtSessionService;
import com.jllado.weightcontrol.security.SessionCookieService;
import com.jllado.weightcontrol.util.DateTimes;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtSessionService jwtSessionService;

    @Mock
    private GoogleIdTokenVerifier verifier;

    @Mock
    private SessionCookieService sessionCookieService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
            new AppProperties.Auth("test-client-id", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(Collections.emptyList()),
            null
        );
        service = new AuthService(userRepository, jwtSessionService, sessionCookieService, verifier);
    }

    @Test
    void loginRejectsEmailOtherThanJlladoGmailCom() throws GeneralSecurityException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(verifier.verify("credential")).thenReturn(token("other@example.com", "google-sub", "Other User"));

        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> service.loginWithGoogle("credential", response)
        );

        assertEquals("Only jllado@gmail.com can log in", exception.getMessage());
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
        verify(jwtSessionService, never()).createToken(any());
        assertNull(response.getHeader("Set-Cookie"));
    }

    @Test
    void loginAllowsJlladoGmailComAndWritesSessionCookie() throws GeneralSecurityException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        User savedUser = new User();
        savedUser.setId(7L);
        savedUser.setEmail("jllado@gmail.com");
        savedUser.setGoogleSub("google-sub");
        savedUser.setDisplayName("J Llado");
        savedUser.setDashboardAnchorDate(LocalDate.now(DateTimes.USER_ZONE));

        when(verifier.verify("credential")).thenReturn(token("jllado@gmail.com", "google-sub", "J Llado"));
        when(userRepository.findByEmail("jllado@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtSessionService.createToken(any(AuthenticatedUser.class))).thenReturn("jwt-token");

        User user = service.loginWithGoogle("credential", response);

        assertEquals(savedUser, user);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertEquals("jllado@gmail.com", createdUser.getEmail());
        assertEquals("google-sub", createdUser.getGoogleSub());
        assertEquals("J Llado", createdUser.getDisplayName());
        assertEquals(LocalDate.now(DateTimes.USER_ZONE), createdUser.getDashboardAnchorDate());
        assertEquals(2983, createdUser.getTypicalCaloriesSaturday());
        assertEquals(2983, createdUser.getTypicalCaloriesSunday());
        assertEquals(1853, createdUser.getTypicalCaloriesMonday());
        assertEquals(1853, createdUser.getTypicalCaloriesTuesday());
        assertEquals(1853, createdUser.getTypicalCaloriesWednesday());
        assertEquals(1853, createdUser.getTypicalCaloriesThursday());
        assertEquals(1122, createdUser.getTypicalCaloriesFriday());

        ArgumentCaptor<AuthenticatedUser> authenticatedUserCaptor = ArgumentCaptor.forClass(AuthenticatedUser.class);
        verify(jwtSessionService).createToken(authenticatedUserCaptor.capture());
        AuthenticatedUser authenticatedUser = authenticatedUserCaptor.getValue();
        assertEquals(7L, authenticatedUser.getUserId());
        assertEquals("jllado@gmail.com", authenticatedUser.getEmail());
        verify(sessionCookieService).writeSessionCookie(response, "jwt-token");
    }

    @Test
    void loginRejectsMismatchedGoogleAccountForImportedUser() throws GeneralSecurityException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        User existingUser = new User();
        existingUser.setId(7L);
        existingUser.setEmail("jllado@gmail.com");
        existingUser.setGoogleSub("stored-sub");

        when(verifier.verify("credential")).thenReturn(token("jllado@gmail.com", "other-sub", "J Llado"));
        when(userRepository.findByEmail("jllado@gmail.com")).thenReturn(Optional.of(existingUser));

        BadRequestException exception = assertThrows(
            BadRequestException.class,
            () -> service.loginWithGoogle("credential", response)
        );

        assertEquals("Google account does not match imported user", exception.getMessage());
        verify(userRepository, never()).save(any());
        verify(jwtSessionService, never()).createToken(any());
    }

    @Test
    void logoutClearsSessionCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.logout(response);

        verify(sessionCookieService).clearSessionCookie(response);
    }

    private static GoogleIdToken token(String email, String subject, String name) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload()
            .setEmail(email)
            .setSubject(subject)
            .set("name", name);
        return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
    }
}
