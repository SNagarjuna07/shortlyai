package com.shortlyai.analytics.clicks;

import java.time.Instant;

// Returns hourly based frequent URLs
public record HourlyBreakdownResponse(Instant hour, long clickCount) {}