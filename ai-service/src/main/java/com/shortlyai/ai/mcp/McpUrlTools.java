package com.shortlyai.ai.mcp;

import com.shortlyai.ai.operations.ResilientUrlOps;
import com.shortlyai.ai.operations.UrlOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * MCP-facing URL tools.
 * RESILIENCE: Delegated to ResilientUrlOps - same CB + Retry config as the
 * agent path, so both surfaces share one circuit state per downstream service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpUrlTools {

    private final ResilientUrlOps resilientUrlOps;

    private void validateUserId(String userId) {

        try {

            UUID.fromString(userId);

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException(
                    "Invalid userId — must be a UUID: " + userId);
        }
    }

    @Tool(name = "mcp_shortenUrl", description = """
            Shorten a long URL. Returns the generated short URL and its numeric urlId.
            Requires the caller's userId (UUID from their JWT subject claim).
            """)
    public String shortenUrl(
            @ToolParam(description = "The original long URL to shorten (must include http:// or https://)") String originalUrl,
            @ToolParam(description = "Authenticated user UUID from JWT sub claim") String userId
    ) {

        validateUserId(userId);

        log.info("MCP shortenUrl userId={} url={}", userId, originalUrl);

        UrlOperationsService.ShortenResult result = resilientUrlOps.shorten(originalUrl, userId);

        if (result == null) {
            return "URL shortening temporarily unavailable — try again shortly.";
        }

        log.debug("MCP shortenUrl slug={} id={}", result.slug(), result.id());

        return "Short URL created: %s (urlId: %d, slug: %s)"
                .formatted(result.shortUrl(), result.id(), result.slug());
    }

    @Tool(name = "mcp_getUrlDetails", description = """
            Get details of a shortened URL by its slug.
            Returns the original URL, short URL, and total click count.
            """)
    public String getUrlDetails(
            @ToolParam(description = "The short slug (e.g. abc123)") String slug,
            @ToolParam(description = "Authenticated user UUID") String userId
    ) {

        validateUserId(userId);

        log.info("MCP getUrlDetails userId={} slug={}", userId, slug);

        UrlOperationsService.UrlDetails details = resilientUrlOps.getDetails(slug, userId);

        if (details == null) {
            return "Could not retrieve details for '%s' — url-service temporarily unavailable."
                    .formatted(slug);
        }

        return "urlId: %d | slug: %s | original: %s | short: %s | clicks: %d"
                .formatted(details.id(), details.slug(), details.originalUrl(),
                        details.shortUrl(), details.clickCount());
    }

    @Tool(name = "mcp_deleteUrl", description = """
            Permanently delete a shortened URL by its slug.
            This action is irreversible — always confirm with the user before calling.
            """)
    public String deleteUrl(
            @ToolParam(description = "The slug of the URL to delete") String slug,
            @ToolParam(description = "Authenticated user UUID — must be the URL's owner") String userId
    ) {

        validateUserId(userId);

        log.warn("MCP deleteUrl userId={} slug={}", userId, slug);

        try {

            resilientUrlOps.delete(slug, userId);

        } catch (ResilientUrlOps.UrlServiceUnavailableException ex) {

            return "Could not delete '%s' — url-service temporarily unavailable.".formatted(slug);
        }

        log.info("MCP deleteUrl completed userId={} slug={}", userId, slug);

        return "Deleted URL with slug: " + slug;
    }
}