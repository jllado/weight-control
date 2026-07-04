package com.jllado.weightcontrol.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "wc_session";
    private static final Duration RENEWAL_THRESHOLD = Duration.ofDays(1);

    private final JwtSessionService jwtSessionService;
    private final SessionCookieService sessionCookieService;

    public SessionAuthenticationFilter(JwtSessionService jwtSessionService, SessionCookieService sessionCookieService) {
        this.jwtSessionService = jwtSessionService;
        this.sessionCookieService = sessionCookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            try {
                AuthenticatedSession authenticatedSession = jwtSessionService.parse(token);
                AuthenticatedUser authenticatedUser = authenticatedSession.user();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    authenticatedUser,
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_USER")
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (!authenticatedSession.expiresAt().isAfter(Instant.now().plus(RENEWAL_THRESHOLD))) {
                    String refreshedToken = jwtSessionService.createToken(authenticatedUser);
                    sessionCookieService.writeSessionCookie(response, refreshedToken);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
            .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }
}
