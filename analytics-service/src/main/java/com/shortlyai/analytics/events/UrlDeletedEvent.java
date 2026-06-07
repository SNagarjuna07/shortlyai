package com.shortlyai.analytics.events;

import java.time.Instant;
import java.util.UUID;

// Must match url-service UrlDeletedEvent field-for-field
// Kafka JsonDeserializer maps JSON by field name — any mismatch = null
public record UrlDeletedEvent(
        Long    id,         // url DB primary key — for logging only
        String  slug,       // we delete click_events by this
        UUID    userId,     // who deleted — for logging only
        Instant deletedAt
) {}