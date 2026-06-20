package com.shortlyai.ai.mcp;

import com.shortlyai.ai.operations.UrlOperationsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpUrlTools {

    private final UrlOperationsService urlOps;

    // userId comes from McpUserContext (set by McpKeyFilter after API key validation)
    // No @ToolParam userId - LLM cannot inject or forge it
    private String authenticatedUserId() {

        String userId = McpUserContext.get();

        if (userId == null) {

            // Should never happen - filter blocks unauthenticated requests before this point
            throw new IllegalStateException("No authenticated userId in MCP context - filter misconfigured");
        }

        return userId;
    }

    @Tool(name = "mcp_shortenUrl", description = """
            Shorten a long URL. Returns the generated short URL and its numeric urlId.
            """)
    @CircuitBreaker(name = "url-service", fallbackMethod = "shortenUrlFallback")
    @Retry(name = "url-service")
    public String shortenUrl(
            @ToolParam(description = "The original long URL to shorten (must include http:// or https://)") String originalUrl
    ) {

        String userId = authenticatedUserId();

        log.info("MCP shortenUrl userId: {}, url: {}", userId, originalUrl);

        try {

            UrlOperationsService.ShortenResult r = urlOps.shorten(originalUrl, userId);

            log.debug("MCP shortenUrl slug: {}, id: {}", r.slug(), r.id());

            return "Short URL created: %s (urlId: %d, slug: %s)".formatted(r.shortUrl(), r.id(), r.slug());

        } catch (HttpClientErrorException e) {

            // 4xx = client error (invalid URL format, bad request) - server is healthy, don't trip CB
            log.warn("MCP shortenUrl 4xx for userId: {}, url: {}, status: {}", userId, originalUrl, e.getStatusCode());

            return "Could not shorten URL: %s (%s)".formatted(originalUrl, e.getStatusText());
        }
    }

    public String shortenUrlFallback(String originalUrl, Throwable ex) {

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
            @ToolParam(description = "The short slug (e.g. abc123)") String slug
    ) {

        String userId = authenticatedUserId();

        log.info("MCP getUrlDetails userId: {}, slug: {}", userId, slug);

        try {

            UrlOperationsService.UrlDetails d = urlOps.getDetails(slug, userId);

            return "urlId: %d | slug: %s | original: %s | short: %s | clicks: %d"
                    .formatted(d.id(), d.slug(), d.originalUrl(), d.shortUrl(), d.clickCount());

        } catch (HttpClientErrorException e) {

            // 404 = slug not found, 403 = not owned by this user
            log.warn("MCP getUrlDetails 4xx userId: {}, slug: {}, status: {}", userId, slug, e.getStatusCode());

            return "Could not retrieve details for '%s': %s".formatted(slug, e.getStatusText());
        }
    }

    public String getUrlDetailsFallback(String slug, Throwable ex) {

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
            @ToolParam(description = "The slug of the URL to delete") String slug
    ) {

        String userId = authenticatedUserId();

        log.warn("MCP deleteUrl userId: {}, slug: {}", userId, slug);

        try {

            urlOps.delete(slug, userId);

            log.info("MCP deleteUrl completed userId: {}, slug: {}", userId, slug);

            return "Deleted URL with slug: " + slug;

        } catch (HttpClientErrorException e) {

            // 404 = slug not found, 403 = not owned by this user - server healthy, don't trip CB
            log.warn("MCP deleteUrl 4xx userId: {}, slug: {}, status: {}", userId, slug, e.getStatusCode());

            return "Could not delete '%s': %s".formatted(slug, e.getStatusText());
        }
    }

    public String deleteUrlFallback(String slug, Throwable ex) {

        log.error("url-service unavailable for MCP deleteUrl, slug: {}", slug, ex);

        return "Could not delete '%s' - url-service temporarily unavailable.".formatted(slug);
    }
}