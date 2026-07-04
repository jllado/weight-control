package com.jllado.weightcontrol.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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

    @Mock
    private JwtSessionService jwtSessionService;

    @Mock
    private SessionCookieService sessionCookieService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWithoutRefreshingWhenTokenHasMoreThanOneDayLeft() throws Exception {
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(jwtSessionService, sessionCookieService);
        MockHttpServletRequest request = requestWithSessionCookie("current-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticatedUser user = new AuthenticatedUser(7L, "jllado@gmail.com");

        when(jwtSessionService.parse("current-token")).thenReturn(
            new AuthenticatedSession(user, Instant.now().plusSeconds(2 * 24 * 60 * 60))
        );

        filter.doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user, authentication.getPrincipal());
        verify(jwtSessionService, never()).createToken(user);
        verify(sessionCookieService, never()).writeSessionCookie(response, "refreshed-token");
    }

    @Test
    void refreshesWhenTokenHasOneDayOrLessLeft() throws Exception {
        SessionAuthenticationFilter filter = new SessionAuthenticationFilter(jwtSessionService, sessionCookieService);
        MockHttpServletRequest request = requestWithSessionCookie("current-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticatedUser user = new AuthenticatedUser(7L, "jllado@gmail.com");

        when(jwtSessionService.parse("current-token")).thenReturn(
            new AuthenticatedSession(user, Instant.now().plusSeconds(12 * 60 * 60))
        );
        when(jwtSessionService.createToken(user)).thenReturn("refreshed-token");

        filter.doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(user, authentication.getPrincipal());
        verify(jwtSessionService).createToken(user);
        verify(sessionCookieService).writeSessionCookie(response, "refreshed-token");
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

    private static MockHttpServletRequest requestWithSessionCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(SessionAuthenticationFilter.COOKIE_NAME, token));
        return request;
    }
}
