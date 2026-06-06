package com.shortlyai.analytics.clicks;

import java.util.UUID;

// Record DTO — immutable, no boilerplate, serializes perfectly to JSON
public record ClickStatsResponse(
        UUID   urlId,
        long   totalClicks,
        String message
) {}