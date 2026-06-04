package com.shortlyai.url.shortening.dto;

import java.time.Instant;

// Returned after successful URL shortening
public record ShortenResponse(
        Long id,
        String slug,
        String shortUrl,      // full URL e.g. http://localhost:8082/abc123
        String originalUrl,
        Long userId,
        boolean isCustom,
        long clickCount,
        Instant expiresAt,
        Instant createdAt
) {}