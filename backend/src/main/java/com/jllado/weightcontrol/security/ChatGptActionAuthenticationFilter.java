package com.jllado.weightcontrol.security;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.User;
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

    public ChatGptActionAuthenticationFilter(AppProperties properties, UserRepository userRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
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
        if (!hasValidToken(request.getHeader("Authorization"))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        User user = userRepository.findByEmail(properties.chatGptActions().userEmail()).orElse(null);
        if (user == null) {
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

    private boolean hasValidToken(String authorization) {
        String token = properties.chatGptActions().token();
        if (token.isBlank() || authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] expected = token.getBytes(StandardCharsets.UTF_8);
        byte[] provided = authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
