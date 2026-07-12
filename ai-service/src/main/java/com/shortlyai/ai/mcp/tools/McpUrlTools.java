package com.shortlyai.ai.mcp.tools;

import com.shortlyai.ai.mcp.auth.McpUserContext;
import com.shortlyai.ai.operations.ResilientUrlOps;
import com.shortlyai.ai.operations.UrlOperationsService;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
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

    @McpTool(name = "shorten-url", description = """
            Never share the URL id in the response.
            Creates a shortened URL for a given long URL. Use this when the user wants
            to shorten, condense, or create a share-friendly link for a URL. Returns the
            new short URL, its slug and redirect link - keep the urlId if you'll
            need get_url_stats afterward, and the slug if you'll need get_url_details
            or delete_url afterward.
            """)
    public String shortenUrl(
            @McpToolParam(description = "The original long URL to shorten (must include http:// or https://)")
            String originalUrl,
            McpSyncRequestContext context
    ) {

        String userId = authenticatedUserId();

        if (context.elicitEnabled()) {

            if (!originalUrl.startsWith("http://")
                    && !originalUrl.startsWith("https://")
            ) {

                return "'%s' does not look like a valid URL. URL must start with http:// or https://"
                        .formatted(originalUrl);
            }
        }

        log.info("MCP tool shorten-url called by userId: {}, url: {}", userId, originalUrl);

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
                        "MCP shorten-url 4xx for userId: {}, url: {}, status: {}",
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

    @McpTool(name = "get-url-details", description = """
            Looks up an EXISTING shortened URL by its slug and returns its original
            URL, short URL, and total click count. Use this when the user refers to a
            URL by its slug or short link and wants to know what it points to or how
            it's performing overall. If the user only wants click-count trends/stats
            and already has the numeric urlId, use get_url_stats instead - it's cheaper
            and more direct. If the user doesn't have a slug at all, they need
            shorten_url first, not this.
            """)
    public String getUrlDetails(
            @McpToolParam(description = "The short slug (e.g. abc123)")
            String slug
    ) {

        String userId = authenticatedUserId();

        log.info("MCP tool get-url-details invoked for userId: {}, slug: {}", userId, slug);

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
                        "MCP get-url-details 4xx userId: {}, slug: {}, status: {}",
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

    // record used by the elicitation
    public record DeleteConfirmation(boolean confirmDeletion) {}

    @McpTool(name = "delete-url", description = """
        Permanently and irreversibly deletes a shortened URL by its slug. This tool
        asks the user to confirm via an interactive prompt itself - call it directly
        as soon as the user expresses intent to delete, do not ask them to confirm
        yourself in conversation first, the tool's own confirmation step handles that.
        """)
    public String deleteUrl(
            @McpToolParam(description = "The slug of the URL to delete")
            String slug,
            McpSyncRequestContext context
    ) {

        String userId = authenticatedUserId();

        log.warn("MCP tool deleteUrl invoked for userId: {}, slug: {}", userId, slug);

        try {

            // Look up what we're about to delete FIRST - the confirmation prompt
            // should show the actual destination URL
            UrlOperationsService.UrlDetails details = resilientUrlOps
                    .getDetails(slug, userId)
                    .join();

            if (details == null) {
                return "Could not find a URL with slug '%s' to delete."
                        .formatted(slug);
            }

            if (context.elicitEnabled()) {

                StructuredElicitResult<DeleteConfirmation> confirmation = context.elicit(
                        e -> e.message(
                                "Delete '%s' -> %s ? This cannot be undone."
                                        .formatted(slug, details.originalUrl())
                        ),
                        DeleteConfirmation.class
                );

                boolean confirmed = confirmation.action() == ElicitResult.Action.ACCEPT
                        && confirmation
                        .structuredContent()
                        .confirmDeletion();

                if (!confirmed) {

                    log.info("MCP deleteUrl userId: {}, slug: {} - user declined confirmation", userId, slug);

                    return "Deletion of '%s' was not confirmed - nothing was deleted.".formatted(slug);
                }

            } else {
                // Client doesn't support elicitation - falls back to trusting the tool
                log.warn("MCP delete-url userId: {}, slug: {} - client has no elicitation support", userId, slug);
            }

            Boolean deleted = resilientUrlOps.delete(slug, userId).join();

            if (Boolean.TRUE.equals(deleted)) {
                return "Deleted URL with slug: " + slug;
            }

            return "Could not delete '%s' - url-service temporarily unavailable.".formatted(slug);

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn("MCP delete-url 4xx userId: {}, slug: {}, status: {}", userId, slug, httpEx.getStatusCode());

                return "Could not delete '%s': %s".formatted(slug, httpEx.getStatusText());
            }

            throw e;
        }
    }
}