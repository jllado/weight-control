package com.jllado.weightcontrol.api.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record GoogleLoginRequest(@NotBlank String credential) {
    }

    public record AuthUserResponse(String email, String displayName, boolean authenticated) {
    }
}
