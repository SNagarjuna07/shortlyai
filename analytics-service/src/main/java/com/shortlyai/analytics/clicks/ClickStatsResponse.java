package com.shortlyai.analytics.clicks;

// Record DTO — immutable, no boilerplate, serializes perfectly to JSON
public record ClickStatsResponse(
        Long   urlId,
        long   totalClicks,
        String message
) {}