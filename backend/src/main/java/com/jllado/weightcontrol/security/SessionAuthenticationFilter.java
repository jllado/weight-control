package com.jllado.weightcontrol.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "wc_session";

    private final JwtSessionService jwtSessionService;

    public SessionAuthenticationFilter(JwtSessionService jwtSessionService) {
        this.jwtSessionService = jwtSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            try {
                AuthenticatedUser authenticatedUser = jwtSessionService.parse(token);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    authenticatedUser,
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_USER")
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
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
