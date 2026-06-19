package com.shortlyai.ai.operations;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientUrlOps {

    private final UrlOperationsService urlOps;

    @CircuitBreaker(name = "url-service", fallbackMethod = "shortenFallback")
    @Retry(name = "url-service")
    public UrlOperationsService.ShortenResult shorten(String originalUrl, String userId) {

        log.debug("Shortening URL for userId: {} url: {}", userId, originalUrl);

        return urlOps.shorten(originalUrl, userId);
    }

    public UrlOperationsService.ShortenResult shortenFallback(
            String originalUrl, String userId, Throwable ex) {

        log.error("url-service is not available. Cannot shorten '{}'. Please try again later", originalUrl);

        return null;
    }

    @CircuitBreaker(name = "url-service", fallbackMethod = "getDetailsFallback")
    @Retry(name = "url-service")
    public UrlOperationsService.UrlDetails getDetails(String slug, String userId) {

        log.debug("Fetching URL details for userId: {} slug: {}", userId, slug);

        return urlOps.getDetails(slug, userId);
    }

    public UrlOperationsService.UrlDetails getDetailsFallback(
            String slug, String userId, Throwable ex) {

        log.error("url-service is not available. Please try again later", ex);

        return null;
    }

    @CircuitBreaker(name = "url-service", fallbackMethod = "deleteFallback")
    @Retry(name = "url-service")
    public boolean delete(String slug, String userId) {

        log.debug("Deleting URL for userId: {} slug: {}", userId, slug);

        urlOps.delete(slug, userId);

        return true;
    }

    public boolean deleteFallback(String slug, String userId, Throwable ex) {

        log.error("url-service unavailable - could not delete slug: {}", slug, ex);

        return false;
    }
}