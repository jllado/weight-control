package com.jllado.weightcontrol.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class PushDtos {

    private PushDtos() {
    }

    public record PushKeysRequest(@NotBlank String p256dh, @NotBlank String auth) {
    }

    public record PushSubscriptionRequest(@NotBlank String endpoint, @NotNull @Valid PushKeysRequest keys) {
    }

    public record PushEndpointRequest(@NotBlank String endpoint) {
    }

    public record PushConfigResponse(boolean enabled, String publicKey, String timeZone) {
    }
}
