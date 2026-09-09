package com.jllado.weightcontrol.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.*;
import com.jllado.weightcontrol.service.CoachAuthAlertService;
import com.jllado.weightcontrol.service.CoachAuthAlertService.Reason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Mock
    private CoachAuthAlertService alerts;

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
        filter = new ChatGptActionAuthenticationFilter(properties, userRepository, alerts);
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
        verifyNoInteractions(alerts);
    }

    @Test
    void rejectsInvalidActionToken() throws Exception {
        MockHttpServletRequest request = actionRequest("Bearer wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        verify(alerts).record(eq("GET"), anyString(), isNull(), eq(Reason.INVALID_TOKEN));
    }

    @Test
    void rejectsMissingActionToken() throws Exception {
        MockHttpServletRequest request = actionRequest(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        verify(alerts).record(eq("GET"), anyString(), isNull(), eq(Reason.MISSING_HEADER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic action-token", "Bearer ", "bearer action-token", ""})
    void reportsMalformedBearerHeaders(String header) throws Exception {
        MockHttpServletRequest request = actionRequest(header);
        request.addHeader("User-Agent", "ChatGPT-User/1.0");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
        verify(alerts).record(eq("GET"), anyString(), eq("ChatGPT-User/1.0"), eq(Reason.MALFORMED_BEARER));
    }

    @Test
    void reportsMissingConfiguredUser() throws Exception {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(actionRequest("Bearer action-token"), response, new MockFilterChain());
        assertEquals(401, response.getStatus());
        verify(alerts).record(eq("GET"), anyString(), isNull(), eq(Reason.USER_NOT_FOUND));
    }

    @Test
    void ignoresOtherEndpoints() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/sleeps"), new MockHttpServletResponse(), new MockFilterChain());
        verifyNoInteractions(alerts, userRepository);
    }

    @Test
    void reportsUnconfiguredToken() throws Exception {
        AppProperties properties = org.mockito.Mockito.mock(AppProperties.class);
        when(properties.chatGptActions()).thenReturn(new AppProperties.ChatGptActions("", "owner@example.com", "", ""));
        filter = new ChatGptActionAuthenticationFilter(properties, userRepository, alerts);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(actionRequest("Bearer action-token"), response, new MockFilterChain());
        assertEquals(401, response.getStatus());
        verify(alerts).record(eq("GET"), anyString(), isNull(), eq(Reason.UNCONFIGURED_TOKEN));
    }

    private MockHttpServletRequest actionRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/chatgpt-actions/coach/progress-photos");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }
}
