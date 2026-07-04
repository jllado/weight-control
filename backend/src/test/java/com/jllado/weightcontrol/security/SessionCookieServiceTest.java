package com.jllado.weightcontrol.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jllado.weightcontrol.config.AppProperties;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class SessionCookieServiceTest {

    @Test
    void writeSessionCookieSetsConfiguredSessionHeader() {
        SessionCookieService service = new SessionCookieService(properties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeSessionCookie(response, "jwt-token");

        assertTrue(response.getHeader("Set-Cookie").startsWith("wc_session=jwt-token; Path=/; Max-Age=604800; Expires="));
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
            new AppProperties.Auth("test-client-id", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(Collections.emptyList()),
            null
        );
    }
}
