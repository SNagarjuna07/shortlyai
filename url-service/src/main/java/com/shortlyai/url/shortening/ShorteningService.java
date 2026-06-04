package com.shortlyai.url.shortening;

import com.shortlyai.url.common.dto.ShortenRequest;
import com.shortlyai.url.common.dto.ShortenResponse;

public interface ShorteningService {

    // Shorten a URL — saves to DB, caches in Redis, publishes Kafka event
    ShortenResponse shorten(ShortenRequest request, Long userId);

    // Resolve a slug to its original URL — Redis first, Postgres fallback
    String resolve(String slug);

    // Soft-delete a URL — only the owner can delete their own URL
    void delete(Long id, Long userId);
}