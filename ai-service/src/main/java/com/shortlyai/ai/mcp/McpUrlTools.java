package com.shortlyai.ai.mcp;

import com.shortlyai.ai.operations.UrlOperationsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpUrlTools {

    private final UrlOperationsService urlOps;

    // MCP clients pass userId as a raw @ToolParam (no ToolContext available in MCP protocol).
    // Validate UUID format here - prevents garbage data reaching url-service.
    // Note: this is format validation only. Proper fix = Spring Security on /mcp/** endpoints.
    private void validateUserId(String userId) {

        try {

            UUID.fromString(userId); // throws IllegalArgumentException if not valid UUID

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException("Invalid userId - must be a UUID: " + userId);
        }
    }

    // @Tool(name = "mcp_...") - explicit names avoid ambiguity vs agent tool names in Spring AI registry
    @Tool(name = "mcp_shortenUrl", description = """
            Shorten a long URL. Returns the generated short URL and its numeric urlId.
            Requires the caller's userId (UUID from their JWT subject claim).
            """)
    @CircuitBreaker(name = "url-service", fallbackMethod = "shortenUrlFallback")
    @Retry(name = "url-service")
    public String shortenUrl(
            @ToolParam(description = "The original long URL to shorten (must include http:// or https://)") String originalUrl,
            @ToolParam(description = "Authenticated user UUID from JWT sub claim") String userId
    ) {

        validateUserId(userId);

        log.info("MCP shortenUrl userId: {}, url: {}", userId, originalUrl);

        UrlOperationsService.ShortenResult r = urlOps.shorten(originalUrl, userId);

        log.debug("MCP shortenUrl slug: {}, id: {}", r.slug(), r.id());

        return "Short URL created: %s (urlId: %d, slug: %s)".formatted(r.shortUrl(), r.id(), r.slug());
    }

    // Fallback signature: same params as method + Throwable last (Resilience4j requirement)
    public String shortenUrlFallback(String originalUrl, String userId, Throwable ex) {

        log.error("url-service unavailable for MCP shortenUrl, url: {}", originalUrl, ex);

        return "URL shortening temporarily unavailable. Try again shortly.";
    }

    @Tool(name = "mcp_getUrlDetails", description = """
            Get details of a shortened URL by its slug.
            Returns the original URL, short URL, and total click count.
            """)
    @CircuitBreaker(name = "url-service", fallbackMethod = "getUrlDetailsFallback")
    @Retry(name = "url-service")
    public String getUrlDetails(
            @ToolParam(description = "The short slug (e.g. abc123)") String slug,
            @ToolParam(description = "Authenticated user UUID") String userId
    ) {

        validateUserId(userId);

        log.info("MCP getUrlDetails userId: {}, slug: {}", userId, slug);

        UrlOperationsService.UrlDetails d = urlOps.getDetails(slug, userId);

        return "urlId: %d | slug: %s | original: %s | short: %s | clicks: %d"
                .formatted(d.id(), d.slug(), d.originalUrl(), d.shortUrl(), d.clickCount());
    }

    public String getUrlDetailsFallback(String slug, String userId, Throwable ex) {

        log.error("url-service unavailable for MCP getUrlDetails, slug: {}", slug, ex);

        return "Could not retrieve details for '%s' - url-service temporarily unavailable.".formatted(slug);
    }

    @Tool(name = "mcp_deleteUrl", description = """
            Permanently delete a shortened URL by its slug.
            This action is irreversible - always confirm with the user before calling.
            """)
    @CircuitBreaker(name = "url-service", fallbackMethod = "deleteUrlFallback")
    @Retry(name = "url-service")
    public String deleteUrl(
            @ToolParam(description = "The slug of the URL to delete") String slug,
            @ToolParam(description = "Authenticated user UUID - must be the URL's owner") String userId
    ) {

        validateUserId(userId);

        log.warn("MCP deleteUrl userId: {}, slug: {}", userId, slug);

        urlOps.delete(slug, userId);

        log.info("MCP deleteUrl completed userId: {}, slug: {}", userId, slug);

        return "Deleted URL with slug: " + slug;
    }

    public String deleteUrlFallback(String slug, String userId, Throwable ex) {

        log.error("url-service unavailable for MCP deleteUrl, slug: {}", slug, ex);

        return "Could not delete '%s' - url-service temporarily unavailable.".formatted(slug);
    }
}