package com.jllado.weightcontrol.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jllado.weightcontrol.config.AppProperties;
import java.util.Collections;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionCookieServiceTest {

    @Test
    void createsThirtyDayJwtAndPreservesItsIdentity() {
        AppProperties properties = properties();
        JwtSessionService sessions = new JwtSessionService(properties);
        AuthenticatedUser user = new AuthenticatedUser(7L, "jllado@gmail.com");

        String token = sessions.createToken(user);
        AuthenticatedSession session = sessions.parse(token);
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(properties.auth().jwtSecret().getBytes(StandardCharsets.UTF_8))).build().parseSignedClaims(token).getPayload();

        assertEquals(user.getUserId(), session.user().getUserId());
        assertEquals(user.getEmail(), session.user().getEmail());
        assertEquals(Duration.ofDays(30), Duration.between(claims.getIssuedAt().toInstant(), session.expiresAt()));
    }

    @Test
    void writeSessionCookieSetsConfiguredSessionHeader() {
        SessionCookieService service = new SessionCookieService(properties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeSessionCookie(response, "jwt-token");

        assertTrue(response.getHeader("Set-Cookie").startsWith("wc_session=jwt-token; Path=/; Max-Age=2592000; Expires="));
        assertTrue(response.getHeader("Set-Cookie").contains("; HttpOnly; SameSite=Lax"));
    }

    @Test
    void clearSessionCookieExpiresSessionHeader() {
        SessionCookieService service = new SessionCookieService(properties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearSessionCookie(response);

        assertTrue(response.getHeader("Set-Cookie").startsWith("wc_session=; Path=/; Max-Age=0; Expires="));
        assertTrue(response.getHeader("Set-Cookie").contains("; HttpOnly; SameSite=Lax"));
    }

    private static AppProperties properties() {
        return new AppProperties(
            new AppProperties.Auth("test-client-id", "test-jwt-secret-test-jwt-secret-32-bytes-long", 30, false),
            new AppProperties.Cors(Collections.emptyList()),
            null,
            new AppProperties.ChatGptActions("", "test@example.com", "https://test.example", "test-file-signing-secret-32-bytes-long"),
            new AppProperties.Push(false, "", "", "mailto:test@example.com", ""),
            new AppProperties.WeeklySummary(false, "", "", "", "")
        );
    }
}
