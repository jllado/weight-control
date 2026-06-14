package com.jllado.weightcontrol.security;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthenticatedUser implements Serializable {
    private final Long userId;
    private final String email;
}
