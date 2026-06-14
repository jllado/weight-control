package com.jllado.weightcontrol.security;

import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new AccessDeniedException("Authentication required");
        }

        return userRepository.findById(principal.getUserId())
            .orElseThrow(() -> new AccessDeniedException("User not found"));
    }
}
