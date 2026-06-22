package com.shortlyai.ai.operations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

// Shared HTTP ops for url-service
// No CB/retry here - resilience handled by ResilientUrlOps
@Service
@Slf4j
public class UrlOperationsService {

    private final RestClient urlServiceClient;

    private final String apiPrefix;

    public UrlOperationsService(
            @Qualifier("urlServiceClient") RestClient urlServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.urlServiceClient = urlServiceClient;
        this.apiPrefix = apiPrefix;
    }

    // url-service ShortenResponse has 9 fields (id, slug, shortUrl, originalUrl,
    // userId, isCustom, clickCount, expiresAt, createdAt).
    // We only need 3 - ignoreUnknown = true prevents UnrecognizedPropertyException.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShortenResult(Long id, String slug, String shortUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UrlDetails(Long id, String slug, String originalUrl, String shortUrl, long clickCount) {}

    public ShortenResult shorten(String originalUrl, String userId) {

        record ShortenRequest(String originalUrl) {}

        log.debug("shorten userId: {}, url: {}", userId, originalUrl);

        return urlServiceClient.post()
                .uri(apiPrefix + "/urls")
                .header("X-User-Id", userId)
                .body(new ShortenRequest(originalUrl))
                .retrieve()
                .body(ShortenResult.class);
    }

    public UrlDetails getDetails(String slug, String userId) {

        log.debug("getDetails userId: {}, slug: {}", userId, slug);

        return urlServiceClient.get()
                .uri(apiPrefix + "/urls/slug/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .body(UrlDetails.class);
    }

    public void delete(String slug, String userId) {

        log.debug("delete userId: {}, slug: {}", userId, slug);

        urlServiceClient.delete()
                .uri(apiPrefix + "/urls/slug/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .toBodilessEntity();
    }
}