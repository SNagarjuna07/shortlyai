package com.shortlyai.url.events;

import java.time.Instant;

public record UrlClickedEvent(
        Long    urlId,      // DB PK — matches analytics UrlClickedEvent
        String  slug,
        String  userAgent,
        String  ipHash,
        String  referer,
        Instant clickedAt
) {}
