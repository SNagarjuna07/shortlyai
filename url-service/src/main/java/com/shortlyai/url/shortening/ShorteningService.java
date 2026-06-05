package com.shortlyai.url.shortening;

import com.shortlyai.url.common.dto.ShortenRequest;
import com.shortlyai.url.common.dto.ShortenResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ShorteningService {

    // Shorten a URL — saves to DB, caches in Redis, publishes Kafka event
    ShortenResponse shorten(ShortenRequest request, Long userId);

    // Resolve a slug to its original URL — Redis first, Postgres fallback
    String resolve(String slug);

    // Soft-delete a URL — only the owner can delete their own URL
    void delete(Long id, Long userId);

    // Get a specific URL of a user
    ShortenResponse getUrl(Long id, Long userId);

    // Get all URLs of a user
    Page<ShortenResponse> getUserUrls(Long userId, Pageable pageable);
}