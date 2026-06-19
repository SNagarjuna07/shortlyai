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

        log.debug("ResilientUrlOps.shorten userId={} url={}", userId, originalUrl);

        return urlOps.shorten(originalUrl, userId);
    }

    public UrlOperationsService.ShortenResult shortenFallback(
            String originalUrl, String userId, Throwable ex) {

        log.error("url-service CB/Retry exhausted for shorten userId={} url={}", userId, originalUrl, ex);

        return null;
    }

    @CircuitBreaker(name = "url-service", fallbackMethod = "getDetailsFallback")
    @Retry(name = "url-service")
    public UrlOperationsService.UrlDetails getDetails(String slug, String userId) {

        log.debug("ResilientUrlOps.getDetails userId={} slug={}", userId, slug);

        return urlOps.getDetails(slug, userId);
    }

    public UrlOperationsService.UrlDetails getDetailsFallback(
            String slug, String userId, Throwable ex) {

        log.error("url-service CB/Retry exhausted for getDetails userId={} slug={}", userId, slug, ex);

        return null;
    }

    @CircuitBreaker(name = "url-service", fallbackMethod = "deleteFallback")
    @Retry(name = "url-service")
    public void delete(String slug, String userId) {

        log.debug("ResilientUrlOps.delete userId={} slug={}", userId, slug);

        urlOps.delete(slug, userId);
    }

    public void deleteFallback(String slug, String userId, Throwable ex) {

        log.error("url-service CB/Retry exhausted for delete userId={} slug={}", userId, slug, ex);

        throw new UrlServiceUnavailableException(
                "url-service unavailable — could not delete slug: " + slug, ex);
    }

    public static class UrlServiceUnavailableException extends RuntimeException {
        public UrlServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}