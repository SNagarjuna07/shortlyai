package com.shortlyai.url.events;

import java.time.Instant;
import java.util.UUID;

public record UrlClickedEvent(
        Long    urlId,      // DB PK — matches analytics UrlClickedEvent
        String  slug,
        String  userAgent,
        String  ipHash,
        String  referer,
        String country,
        String city,
        Instant clickedAt,
        UUID userId
) {}
