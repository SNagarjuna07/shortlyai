package com.shortlyai.auth.dto;

import com.shortlyai.auth.user.Provider;
import com.shortlyai.auth.user.Role;

import java.time.Instant;
import java.util.UUID;

// This record controls exactly what the API reveals
public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        Provider provider,
        boolean verified,
        Instant createdAt
) {}
