package com.shortlyai.ai.events.dto;

import java.time.Instant;
import java.util.UUID;

// MUST match url-service's published url.created event field-for-field.
// Go check url-service's event record and align names exactly - Jackson
// maps by field name, mismatch = silent nulls or deserialization error.
public record UrlCreatedEvent(
        Long urlId,
        UUID userId,
        String originalUrl,
        String slug,
        Instant createdAt
) {}