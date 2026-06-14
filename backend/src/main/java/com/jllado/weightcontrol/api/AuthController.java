package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.api.dto.AuthDtos.AuthUserResponse;
import com.jllado.weightcontrol.api.dto.AuthDtos.GoogleLoginRequest;
import com.jllado.weightcontrol.domain.User;
import com.jllado.weightcontrol.security.CurrentUserService;
import com.jllado.weightcontrol.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUserService;

    public AuthController(AuthService authService, CurrentUserService currentUserService) {
        this.authService = authService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/google")
    public AuthUserResponse login(@Valid @RequestBody GoogleLoginRequest request, HttpServletResponse response) {
        User user = authService.loginWithGoogle(request.credential(), response);
        return new AuthUserResponse(user.getEmail(), user.getDisplayName(), true);
    }

    @GetMapping("/me")
    public AuthUserResponse me() {
        User user = currentUserService.requireUser();
        return new AuthUserResponse(user.getEmail(), user.getDisplayName(), true);
    }

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        authService.logout(response);
    }
}
