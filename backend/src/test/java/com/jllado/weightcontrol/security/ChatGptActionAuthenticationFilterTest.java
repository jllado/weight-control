package com.jllado.weightcontrol.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ChatGptActionAuthenticationFilterTest {

    @Mock
    private UserRepository userRepository;

    private ChatGptActionAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
            new AppProperties.Auth("client", "test-jwt-secret-test-jwt-secret", 7, false),
            new AppProperties.Cors(List.of()),
            new AppProperties.Storage(Path.of("data")),
            new AppProperties.ChatGptActions("action-token", "owner@example.com", "https://test.example", "test-file-signing-secret-32-bytes-long"),
            new AppProperties.Push(false, "", "", "mailto:test@example.com", ""),
            new AppProperties.WeeklySummary(false, "", "", "", "")
        );
        filter = new ChatGptActionAuthenticationFilter(properties, userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesActionTokenAsConfiguredUser() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setEmail("owner@example.com");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        MockHttpServletRequest request = actionRequest("Bearer action-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        AuthenticatedUser principal = assertInstanceOf(
            AuthenticatedUser.class,
            SecurityContextHolder.getContext().getAuthentication().getPrincipal()
        );
        assertEquals(42L, principal.getUserId());
        assertEquals("ROLE_CHATGPT_ACTION", SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator().next().getAuthority());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsInvalidActionToken() throws Exception {
        MockHttpServletRequest request = actionRequest("Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsMissingActionToken() throws Exception {
        MockHttpServletRequest request = actionRequest(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest actionRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chatgpt-actions/coach/progress-photos");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }
}
