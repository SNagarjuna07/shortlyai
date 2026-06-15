package com.shortlyai.ai.agent.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class UrlServiceTools {

    private final RestClient urlServiceClient;
    private final String apiPrefix;

    public UrlServiceTools(
            @Qualifier("urlServiceClient") RestClient urlServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.urlServiceClient = urlServiceClient;
        this.apiPrefix = apiPrefix;
    }

    @Tool(description = "Shorten a long URL and return the generated short URL")
    // name = matches resilience4j.circuitbreaker.instances.[name] in yaml.
    // Retry wraps inside CircuitBreaker by default - each retry attempt
    // counts toward the breaker's sliding window.
    @CircuitBreaker(name = "url-service", fallbackMethod = "shortenUrlFallback")
    @Retry(name = "url-service")
    public String shortenUrl(
            @ToolParam(description = "the original long URL") String originalUrl,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool shortenUrl invoked userId: {}, originalUrl: {}", userId, originalUrl);

        record ShortenRequest(String originalUrl) {}
        record ShortenResponse(Long id, String slug, String shortUrl) {}

        ShortenResponse response = urlServiceClient.post()
                .uri(apiPrefix + "/urls")
                .header("X-User-Id", userId)
                .body(new ShortenRequest(originalUrl))
                .retrieve()
                .body(ShortenResponse.class);

        log.debug("shortenUrl result userId: {}, slug: {}, urlId: {}",
                userId, response.slug(), response.id());

        return "Short URL created: %s (urlId: %d)".formatted(response.shortUrl(), response.id());
    }

    // Fallback signature: SAME params as original + Throwable last.
    // Called when: circuit OPEN, or retries exhausted on ResourceAccessException
    // / HttpServerErrorException. HttpClientErrorException (4xx) is in
    // ignoreExceptions - never reaches here, propagates to GlobalExceptionHandler.
    public String shortenUrlFallback(String originalUrl, ToolContext toolContext, Throwable ex) {

        log.error("url-service unavailable for shortenUrl, originalUrl: {}", originalUrl, ex);

        return "URL shortening is temporarily unavailable. Please try again in a moment.";
    }

    @Tool(description = "Get details of a shortened URL by its slug")
    @CircuitBreaker(name = "url-service", fallbackMethod = "getUrlDetailsFallback")
    @Retry(name = "url-service")
    public String getUrlDetails(
            @ToolParam(description = "the short slug, e.g. abc123") String slug,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getUrlDetails invoked userId: {}, slug: {}", userId, slug);

        @JsonIgnoreProperties(ignoreUnknown = true)
        record UrlDetails(Long id, String slug, String originalUrl, String shortUrl, long clickCount) {}

        UrlDetails details = urlServiceClient.get()
                .uri(apiPrefix + "/urls/slug/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .body(UrlDetails.class);

        log.debug("getUrlDetails result userId: {}, slug: {}, clicks: {}",
                userId, slug, details.clickCount());

        return "urlId: %d, Slug: %s, Original URL: %s, Clicks: %d"
                .formatted(details.id(), details.slug(), details.originalUrl(), details.clickCount());
    }

    public String getUrlDetailsFallback(String slug, ToolContext toolContext, Throwable ex) {

        log.error("url-service unavailable for getUrlDetails, slug: {}", slug, ex);

        return "Could not retrieve details for '%s' - url-service is temporarily unavailable.".formatted(slug);
    }

    @Tool(description = "Delete a shortened URL by its slug")
    @CircuitBreaker(name = "url-service", fallbackMethod = "deleteUrlFallback")
    @Retry(name = "url-service")
    public String deleteUrl(
            @ToolParam(description = "the short slug to delete") String slug,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.warn("Tool deleteUrl invoked userId: {}, slug: {}", userId, slug);

        urlServiceClient.delete()
                .uri(apiPrefix + "/urls/slug/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .toBodilessEntity();

        log.info("deleteUrl completed userId: {}, slug: {}", userId, slug);

        return "Deleted URL with slug: " + slug;
    }

    public String deleteUrlFallback(String slug, ToolContext toolContext, Throwable ex) {

        log.error("url-service unavailable for deleteUrl, slug: {}", slug, ex);

        return "Could not delete '%s' - url-service is temporarily unavailable. Please try again shortly."
                .formatted(slug);
    }
}