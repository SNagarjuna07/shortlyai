package com.shortlyai.url.shortening;

import com.shortlyai.url.common.dto.ShortenRequest;
import com.shortlyai.url.common.dto.ShortenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ShorteningService {

    // Shorten a URL — saves to DB, caches in Redis, publishes Kafka event
    ShortenResponse shorten(ShortenRequest request, UUID userId);

    // Resolve a slug to its original URL — Redis first, Postgres fallback
    String resolve(String slug, HttpServletRequest request);

    // Soft-delete a URL — only the owner can delete their own URL
    void delete(Long id, UUID userId);

    // Get a specific URL of a user
    ShortenResponse getUrl(Long id, UUID userId);

    // Get all URLs of a user
    Page<ShortenResponse> getUserUrls(UUID userId, Pageable pageable);

    // Get URL based on slug
    ShortenResponse getUrlBySlug(String slug, UUID userId);

    // Delete URL by slug
    void deleteUrl(String slug, UUID userId);
}