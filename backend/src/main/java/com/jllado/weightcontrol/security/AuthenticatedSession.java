package com.jllado.weightcontrol.security;

import java.time.Instant;

public record AuthenticatedSession(AuthenticatedUser user, Instant expiresAt) {
}
