package com.shortlyai.ai.operations;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientUrlOps {

    private final UrlOperationsService urlOps;

    private final Executor resilientOpsExecutor;

    @CircuitBreaker(name = "url-service", fallbackMethod = "shortenFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<UrlOperationsService.ShortenResult> shorten(
            String originalUrl,
            String userId
    ) {

        log.debug("Shortening URL for userId: {} url: {}", userId, originalUrl);

        return CompletableFuture.supplyAsync(
                () -> urlOps.shorten(
                        originalUrl,
                        userId
                ),
                resilientOpsExecutor
        );
    }

    public CompletableFuture<UrlOperationsService.ShortenResult> shortenFallback(
            String originalUrl,
            String userId,
            Throwable ex
    ) {

        log.error("url-service is not available. Cannot shorten '{}'. Please try again later", originalUrl, ex);

        return CompletableFuture.completedFuture(null);
    }

    @CircuitBreaker(name = "url-service", fallbackMethod = "getDetailsFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<UrlOperationsService.UrlDetails> getDetails(
            String slug,
            String userId
    ) {

        log.debug("Fetching URL details for userId: {} slug: {}", userId, slug);

        return CompletableFuture.supplyAsync(
                () -> urlOps.getDetails(
                        slug,
                        userId
                ),
                resilientOpsExecutor
        );
    }

    public CompletableFuture<UrlOperationsService.UrlDetails> getDetailsFallback(
            String slug,
            String userId,
            Throwable ex
    ) {

        log.error("url-service is not available while fetching slug: {}. Please try again later", slug, ex);

        return CompletableFuture.completedFuture(null);
    }

    @CircuitBreaker(name = "url-service", fallbackMethod = "deleteFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<Boolean> delete(String slug, String userId) {

        log.debug("Deleting URL for userId: {} slug: {}", userId, slug);

        return CompletableFuture.supplyAsync(() -> {
                    urlOps.delete(slug, userId);
                    return true;
                },
                resilientOpsExecutor
        );
    }

    public CompletableFuture<Boolean> deleteFallback(String slug, String userId, Throwable ex) {

        log.error("url-service unavailable - could not delete slug: {}", slug, ex);

        return CompletableFuture.completedFuture(false);
    }
}