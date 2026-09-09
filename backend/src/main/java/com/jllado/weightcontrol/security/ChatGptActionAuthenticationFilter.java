package com.jllado.weightcontrol.security;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.service.CoachAuthAlertService;
import com.jllado.weightcontrol.service.CoachAuthAlertService.Reason;
import com.jllado.weightcontrol.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ChatGptActionAuthenticationFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/api/chatgpt-actions/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final CoachAuthAlertService alerts;

    public ChatGptActionAuthenticationFilter(AppProperties properties, UserRepository userRepository, CoachAuthAlertService alerts) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.alerts = alerts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Reason failure = authenticationFailure(request.getHeader("Authorization"));
        if (failure != null) {
            alerts.record(request.getMethod(), request.getRequestURI(), request.getHeader("User-Agent"), failure);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        User user = userRepository.findByEmail(properties.chatGptActions().userEmail()).orElse(null);
        if (user == null) {
            alerts.record(request.getMethod(), request.getRequestURI(), request.getHeader("User-Agent"), Reason.USER_NOT_FOUND);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            AuthorityUtils.createAuthorityList("ROLE_CHATGPT_ACTION")
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private Reason authenticationFailure(String authorization) {
        String token = properties.chatGptActions().token();
        if (token.isBlank()) {
            return Reason.UNCONFIGURED_TOKEN;
        }
        if (authorization == null) {
            return Reason.MISSING_HEADER;
        }
        if (!authorization.startsWith(BEARER_PREFIX) || authorization.length() == BEARER_PREFIX.length()) {
            return Reason.MALFORMED_BEARER;
        }
        byte[] expected = token.getBytes(StandardCharsets.UTF_8);
        byte[] provided = authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided) ? null : Reason.INVALID_TOKEN;
    }
}
