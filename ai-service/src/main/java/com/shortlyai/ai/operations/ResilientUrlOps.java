package com.shortlyai.ai.operations;

import com.shortlyai.ai.mcp.resources.UrlResources;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

    @CircuitBreaker(name = "url-service", fallbackMethod = "getDetailsByIdFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<UrlOperationsService.UrlDetails> getDetailsById(
            Long urlId,
            String userId
    ) {

        log.info("Fetching URL details for userId: {} ", userId);

        return CompletableFuture.supplyAsync(
                () -> urlOps.getDetailsById(urlId, userId),
                resilientOpsExecutor
        );
    }

    public CompletableFuture<UrlOperationsService.UrlDetails> getDetailsByIdFallback(Long urlId, String userId, Throwable ex) {

        log.error("url-service unavailable - could not fetch URL with ID: {}", urlId, ex);

        return CompletableFuture.completedFuture(null);
    }

    @CircuitBreaker(name = "url-service", fallbackMethod = "getAllForUserFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<List<UrlResources.UrlResource>> getAllForUser(String userId) {

        log.info("Fetching all URLs for userId: {} ", userId);

        return CompletableFuture.supplyAsync(
                () -> urlOps.getAllUrlsForUser(userId),
                resilientOpsExecutor
        );

    }

    public CompletableFuture<List<UrlResources.UrlResource>>getAllForUserFallback(String userId, Throwable ex) {

        log.error("url-service unavailable - could not fetch all URLs for userId: {}", userId, ex);

        return CompletableFuture.completedFuture(null);
    }
}