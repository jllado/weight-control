package com.jllado.weightcontrol.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class SessionAuthenticationFilterTest {

    private static final String JWT_SECRET = "test-jwt-secret-test-jwt-secret-32-bytes-long";

    @Mock
    private JwtSessionService jwtSessionService;

    @Mock
    private SessionCookieService sessionCookieService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void renewsAnActiveLegacySessionOnEveryBrowserApiRequest() throws Exception {
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(jwtSessionService, sessionCookieService);
        MockHttpServletRequest request = requestWithSessionCookie("current-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticatedUser user = new AuthenticatedUser(7L, "jllado@gmail.com");

        when(jwtSessionService.parse("current-token")).thenReturn(
            new AuthenticatedSession(user, Instant.now().plusSeconds(6 * 24 * 60 * 60))
        );
        when(jwtSessionService.createToken(user)).thenReturn("refreshed-token");

        filter.doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user, authentication.getPrincipal());
        verify(jwtSessionService).createToken(user);
        verify(sessionCookieService).writeSessionCookie(response, "refreshed-token");
    }

    @Test
    void doesNotRenewMachineRequestsLogoutOrPreflight() throws Exception {
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(jwtSessionService, sessionCookieService);
        AuthenticatedUser user = new AuthenticatedUser(7L, "jllado@gmail.com");
        when(jwtSessionService.parse("current-token")).thenReturn(
            new AuthenticatedSession(user, Instant.now().plusSeconds(6 * 24 * 60 * 60))
        );
        for (var route : new Object[][]{{"/api/auth/logout", "POST"}, {"/api/push/release-notification", "POST"}, {"/api/chatgpt-actions/weekly-reflection", "POST"}, {"/api/private", "OPTIONS"}}) {
            MockHttpServletRequest request = requestWithSessionCookie("current-token");
            request.setRequestURI((String) route[0]);
            request.setMethod((String) route[1]);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            SecurityContextHolder.clearContext();
        }
        verify(jwtSessionService, never()).createToken(user);
        verify(sessionCookieService, never()).writeSessionCookie(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void clearsAuthenticationWhenTokenIsInvalid() throws Exception {
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(jwtSessionService, sessionCookieService);
        MockHttpServletRequest request = requestWithSessionCookie("current-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtSessionService.parse("current-token")).thenThrow(new RuntimeException("bad token"));

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtSessionService, never()).createToken(new AuthenticatedUser(7L, "jllado@gmail.com"));
        verify(sessionCookieService, never()).writeSessionCookie(response, "refreshed-token");
    }

    @Test
    void renewsRealSevenDayTokenPastItsOriginalExpiryAndExpiresAfterThirtyIdleDays() throws Exception {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        MutableClock clock = new MutableClock(start);
        AppProperties properties = properties();
        JwtSessionService realSessions = new JwtSessionService(properties, clock);
        SessionCookieService realCookies = new SessionCookieService(properties);
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(realSessions, realCookies);
        AuthenticatedUser user = new AuthenticatedUser(7L, "jllado@gmail.com");
        String legacyToken = Jwts.builder().subject(user.getUserId().toString()).claim("email", user.getEmail())
            .issuedAt(Date.from(start)).expiration(Date.from(start.plus(Duration.ofDays(7))))
            .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8))).compact();

        clock.set(start.plus(Duration.ofDays(6)));
        MockHttpServletRequest daySixRequest = requestWithSessionCookie(legacyToken);
        MockHttpServletResponse daySixResponse = new MockHttpServletResponse();
        filter.doFilter(daySixRequest, daySixResponse, new MockFilterChain());
        String daySixToken = cookieToken(daySixResponse);
        assertEquals(start.plus(Duration.ofDays(36)), realSessions.parse(daySixToken).expiresAt());
        assertTrue(daySixResponse.getHeader("Set-Cookie")
            .contains("; Path=/; Max-Age=2592000; Expires="));
        assertTrue(daySixResponse.getHeader("Set-Cookie").contains("; HttpOnly"));
        assertTrue(daySixResponse.getHeader("Set-Cookie").contains("; Secure"));
        assertTrue(daySixResponse.getHeader("Set-Cookie").contains("; SameSite=Lax"));

        clock.set(start.plus(Duration.ofDays(8)));
        MockHttpServletRequest dayEightRequest = requestWithSessionCookie(daySixToken);
        MockHttpServletResponse dayEightResponse = new MockHttpServletResponse();
        filter.doFilter(dayEightRequest, dayEightResponse, new MockFilterChain());
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(user.getUserId(), authenticatedUser.getUserId());
        assertEquals(user.getEmail(), authenticatedUser.getEmail());
        String dayEightToken = cookieToken(dayEightResponse);
        assertEquals(start.plus(Duration.ofDays(38)), realSessions.parse(dayEightToken).expiresAt());

        SecurityContextHolder.clearContext();
        clock.set(start.plus(Duration.ofDays(38)).plusSeconds(1));
        MockHttpServletRequest idleRequest = requestWithSessionCookie(dayEightToken);
        MockHttpServletResponse idleResponse = new MockHttpServletResponse();
        filter.doFilter(idleRequest, idleResponse, new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(idleResponse.getHeader("Set-Cookie"));
    }

    @Test
    void doesNotAuthenticateOrRenewARealTamperedJwt() throws Exception {
        AppProperties properties = properties();
        JwtSessionService realSessions = new JwtSessionService(properties, new MutableClock(Instant.parse("2026-01-01T00:00:00Z")));
        String validToken = realSessions.createToken(new AuthenticatedUser(7L, "jllado@gmail.com"));
        int signatureStart = validToken.lastIndexOf('.') + 1;
        String tamperedToken = validToken.substring(0, signatureStart)
            + (validToken.charAt(signatureStart) == 'A' ? 'B' : 'A') + validToken.substring(signatureStart + 1);
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(realSessions, new SessionCookieService(properties));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithSessionCookie(tamperedToken), response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(response.getHeader("Set-Cookie"));
    }

    private static MockHttpServletRequest requestWithSessionCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/private");
        request.setCookies(new jakarta.servlet.http.Cookie(SessionAuthenticationFilter.COOKIE_NAME, token));
        return request;
    }

    private static String cookieToken(MockHttpServletResponse response) {
        return response.getHeader("Set-Cookie").split(";", 2)[0]
            .substring(SessionAuthenticationFilter.COOKIE_NAME.length() + 1);
    }

    private static AppProperties properties() {
        return new AppProperties(
            new AppProperties.Auth("test-client", JWT_SECRET, 30, true),
            new AppProperties.Cors(List.of()),
            null,
            new AppProperties.ChatGptActions("", "test@example.com", "https://test.example", "test-file-signing-secret-32-bytes-long"),
            new AppProperties.Push(false, "", "", "mailto:test@example.com", ""),
            new AppProperties.WeeklySummary(false, "", "", "", "")
        );
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        private MutableClock(Instant instant) {
            this(new AtomicReference<>(instant), ZoneOffset.UTC);
        }

        private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void set(Instant instant) {
            this.instant.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
