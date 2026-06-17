package com.shortlyai.ai.agent.tools;

import com.shortlyai.ai.operations.UrlOperationsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UrlServiceTools {

    // No RestClient here HTTP logic lives in UrlOperationsService
    private final UrlOperationsService urlOps;

    @Tool(description = "Shorten a long URL and return the generated short URL")
    @CircuitBreaker(name = "url-service", fallbackMethod = "shortenUrlFallback")
    @Retry(name = "url-service")
    public String shortenUrl(
            @ToolParam(description = "the original long URL") String originalUrl,
            ToolContext toolContext // Spring AI injects this - not exposed to LLM
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool shortenUrl userId: {}, url: {}", userId, originalUrl);

        UrlOperationsService.ShortenResult r = urlOps.shorten(originalUrl, userId);

        log.debug("shortenUrl slug: {}, urlId: {}", r.slug(), r.id());

        return "Short URL created: %s (urlId: %d)".formatted(r.shortUrl(), r.id());
    }

    public String shortenUrlFallback(String originalUrl, ToolContext toolContext, Throwable ex) {

        log.error("url-service unavailable for shortenUrl, url: {}", originalUrl, ex);

        return "URL shortening temporarily unavailable. Try again in a moment.";
    }

    @Tool(description = "Get details of a shortened URL by its slug")
    @CircuitBreaker(name = "url-service", fallbackMethod = "getUrlDetailsFallback")
    @Retry(name = "url-service")
    public String getUrlDetails(
            @ToolParam(description = "the short slug, e.g. abc123") String slug,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getUrlDetails userId: {}, slug: {}", userId, slug);

        UrlOperationsService.UrlDetails d = urlOps.getDetails(slug, userId);

        log.debug("getUrlDetails slug: {}, clicks: {}", slug, d.clickCount());

        return "urlId: %d, Slug: %s, Original URL: %s, Clicks: %d"
                .formatted(d.id(), d.slug(), d.originalUrl(), d.clickCount());
    }

    public String getUrlDetailsFallback(String slug, ToolContext toolContext, Throwable ex) {

        log.error("url-service unavailable for getUrlDetails, slug: {}", slug, ex);

        return "Could not retrieve details for '%s' - url-service temporarily unavailable.".formatted(slug);
    }

    @Tool(description = "Delete a shortened URL by its slug")
    @CircuitBreaker(name = "url-service", fallbackMethod = "deleteUrlFallback")
    @Retry(name = "url-service")
    public String deleteUrl(
            @ToolParam(description = "the short slug to delete") String slug,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.warn("Tool deleteUrl userId: {}, slug: {}", userId, slug);

        urlOps.delete(slug, userId);

        log.info("deleteUrl completed userId: {}, slug: {}", userId, slug);

        return "Deleted URL with slug: " + slug;
    }

    public String deleteUrlFallback(String slug, ToolContext toolContext, Throwable ex) {

        log.error("url-service unavailable for deleteUrl, slug: {}", slug, ex);

        return "Could not delete '%s' - url-service temporarily unavailable.".formatted(slug);
    }
}