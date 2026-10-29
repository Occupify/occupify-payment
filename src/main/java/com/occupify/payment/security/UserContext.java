package com.occupify.payment.security;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserContext {
    private final UUID userId;
    private final String email;
    private final String role;
    private final String correlationId;
}
