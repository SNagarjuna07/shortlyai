package com.shortlyai.auth.dto;

import java.time.Instant;

// Returned on all errors — consistent shape across all 5 services
// message — human readable
// path — which endpoint failed
// status — HTTP status code
public record ErrorResponse(
        int status,
        String message,
        String path,
        Instant timestamp
) {}
