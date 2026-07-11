package com.shortlyai.ai.mcp.tools;

import com.shortlyai.ai.mcp.McpUserContext;
import com.shortlyai.ai.operations.ResilientUrlOps;
import com.shortlyai.ai.operations.UrlOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.concurrent.CompletionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpUrlTools {

    private final ResilientUrlOps resilientUrlOps;

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

    @McpTool(name = "shorten_Url", description = """
            Shorten a long URL. Returns the generated short URL and its redirect link.
            Never share the URL id in the response.
            """)
    public String shortenUrl(
            @McpToolParam(description = "The original long URL to shorten (must include http:// or https://)") String originalUrl
    ) {

        String userId = authenticatedUserId();

        log.info("MCP shortenUrl userId: {}, url: {}", userId, originalUrl);

        try {

            UrlOperationsService.ShortenResult r = resilientUrlOps
                    .shorten(originalUrl, userId)
                    .join(); //.get() -> throws ExecutionException, .join() -> CompletionException (unchecked)

            if (r == null) {
                return "URL shortening temporarily unavailable. Try again shortly.";
            }

            return "Short URL created: %s (urlId: %d, slug: %s)"
                    .formatted(r.shortUrl(), r.id(), r.slug());

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn(
                        "MCP shorten_Url 4xx for userId: {}, url: {}, status: {}",
                        userId,
                        originalUrl,
                        httpEx.getStatusCode()
                );

                return "Could not shorten URL: %s (%s)"
                        .formatted(originalUrl, httpEx.getStatusText());
            }

            throw e;
        }
    }

    @McpTool(name = "get_Url_Details", description = """
            Get details of a shortened URL by its slug.
            Returns the original URL, short URL, and total click count.
            """)
    public String getUrlDetails(
            @McpToolParam(description = "The short slug (e.g. abc123)") String slug
    ) {

        String userId = authenticatedUserId();

        log.info("MCP getUrlDetails userId: {}, slug: {}", userId, slug);

        try {

            UrlOperationsService.UrlDetails d = resilientUrlOps
                    .getDetails(slug, userId)
                    .join();

            if (d == null) {
                return "Could not retrieve details for '%s' - url-service temporarily unavailable."
                        .formatted(slug);
            }

            return "urlId: %d | slug: %s | original: %s | short: %s | clicks: %d"
                    .formatted(
                            d.id(),
                            d.slug(),
                            d.originalUrl(),
                            d.shortUrl(),
                            d.clickCount()
                    );

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn(
                        "MCP getUrlDetails 4xx userId: {}, slug: {}, status: {}",
                        userId,
                        slug,
                        httpEx.getStatusCode()
                );

                return "Could not retrieve details for '%s': %s"
                        .formatted(slug, httpEx.getStatusText());
            }

            throw e;
        }
    }

    @McpTool(name = "delete_Url", description = """
            Permanently delete a shortened URL by its slug.
            This action is irreversible - always confirm with the user before calling.
            """)
    public String deleteUrl(
            @McpToolParam(description = "The slug of the URL to delete") String slug
    ) {

        String userId = authenticatedUserId();

        log.warn("MCP deleteUrl userId: {}, slug: {}", userId, slug);

        try {

            Boolean deleted = resilientUrlOps
                    .delete(slug, userId)
                    .join();

            if (Boolean.TRUE.equals(deleted)) {

                return "Deleted URL with slug: " + slug;
            }

            return "Could not delete '%s' - url-service temporarily unavailable."
                    .formatted(slug);

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn("MCP deleteUrl 4xx userId: {}, slug: {}, status: {}", userId, slug, httpEx.getStatusCode());

                return "Could not delete '%s': %s".formatted(slug, httpEx.getStatusText());
            }

            throw e;
        }
    }
}