package com.jllado.weightcontrol.security;

import com.jllado.weightcontrol.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class SessionCookieService {

    private final AppProperties properties;

    public SessionCookieService(AppProperties properties) {
        this.properties = properties;
    }

    public void writeSessionCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(SessionAuthenticationFilter.COOKIE_NAME, token)
            .httpOnly(true)
            .secure(properties.auth().secureCookie())
            .sameSite("Lax")
            .path("/")
            .maxAge(properties.auth().jwtExpiration())
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(SessionAuthenticationFilter.COOKIE_NAME, "")
            .httpOnly(true)
            .secure(properties.auth().secureCookie())
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
