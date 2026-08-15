package com.jllado.weightcontrol.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jllado.weightcontrol.config.AppProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class PushReleaseAuthenticationFilterTest {

    private PushReleaseAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("", "test@example.com"),
            new AppProperties.Push(true, "public", "private", "mailto:test@example.com", "release-token"),
            new AppProperties.WeeklySummary(false, "", "", "", "")
        );
        filter = new PushReleaseAuthenticationFilter(properties);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesReleaseToken() throws Exception {
        MockHttpServletRequest request = releaseRequest("Bearer release-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("ROLE_PUSH_RELEASE", SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator().next().getAuthority());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsInvalidReleaseToken() throws Exception {
        MockHttpServletRequest request = releaseRequest("Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsMissingReleaseToken() throws Exception {
        MockHttpServletRequest request = releaseRequest(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest releaseRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/push/release-notification");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }
}
