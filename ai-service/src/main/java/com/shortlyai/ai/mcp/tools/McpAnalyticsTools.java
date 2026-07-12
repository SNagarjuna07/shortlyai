package com.shortlyai.ai.mcp.tools;

import com.shortlyai.ai.mcp.auth.McpUserContext;
import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.operations.ResilientAnalyticsOps;
import com.shortlyai.ai.operations.ResilientUrlOps;
import com.shortlyai.ai.operations.UrlOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.concurrent.CompletionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpAnalyticsTools {

    private final ResilientAnalyticsOps resilientAnalyticsOps;

    private final ResilientUrlOps resilientUrlOps;

    // userId from McpKeyFilter-injected context — not from LLM input
    private String authenticatedUserId() {

        String userId = McpUserContext.get();

        if (userId == null) {

            throw new IllegalStateException("No authenticated userId in MCP context - filter misconfigured");
        }

        return userId;
    }

    @McpTool(name = "get-url-stats", description = """
            Returns the total click count for a shortened URL by its slug. Use this
            for quick click-count checks. If you also need the original destination
            URL, call get_url_details instead - it returns both in one call, making
            a separate get_url_stats call unnecessary in that case.
            """)
    public String getUrlStats(
            @McpToolParam(description = "The short slug of the URL (e.g. 'abc123')", required = true)
            String slug
    ) {

        String userId = authenticatedUserId();

        log.info("MCP tool get-url-stats invoked for userId: {}, slug: {}", userId, slug);

        try {

            // analytics-service's getStats is keyed by numeric urlId, not slug -
            // resolve it via url-service first.
            UrlOperationsService.UrlDetails details = resilientUrlOps
                    .getDetails(slug, userId)
                    .join();

            if (details == null) {
                return "Could not find a URL with slug '%s'.".formatted(slug);
            }

            AnalyticsOperationsService.StatsResult stats = resilientAnalyticsOps
                    .getStats(details.id(), userId)
                    .join();

            if (stats == null) {
                return "Click stats for '%s' temporarily unavailable."
                        .formatted(slug);
            }

            return "URL '%s' has %d total clicks"
                    .formatted(slug, stats.totalClicks());

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn(
                        "MCP get-url-stats 4xx userId: {}, slug: {}, status: {}",
                        userId,
                        slug,
                        httpEx.getStatusCode()
                );

                return "Could not retrieve stats for '%s': %s"
                        .formatted(slug, httpEx.getStatusText());
            }

            throw e;
        }
    }

    @McpTool(name = "get-top-urls", description = """ 
            Returns the user's best-performing shortened URLs, ranked by total click
            count, highest first. Use this for "what's my most popular/top link"
            type requests, or when the user wants an overview rather than one specific
            URL. Each result includes the slug, original URL, shortened URL and click
            count - no follow-up call needed for basic info.
            """)
    public String getTopUrls(
            @McpToolParam(description = "How many top URLs to return (e.g. 5)", required = true)
            int limit
    ) {

        String userId = authenticatedUserId();

        log.info("MCP get-top-urls invoked for userId: {}, limit: {}", userId, limit);

        try {

            List<AnalyticsOperationsService.TopUrlResult> topUrls =
                    resilientAnalyticsOps
                            .getTopUrls(limit, userId)
                            .join();

            if (topUrls.isEmpty()) {
                return "No URLs found.";
            }

            StringBuilder sb = new StringBuilder("Top %d URLs:%n".formatted(topUrls.size()));

            for (AnalyticsOperationsService.TopUrlResult url : topUrls) {

                // N calls to url-service here, one per top-url entry - see note
                // above the class. Fine at small limits, worth revisiting if the
                // max limit ever grows.
                UrlOperationsService.UrlDetails details = resilientUrlOps
                        .getDetailsById(url.urlId(), userId)
                        .join();

                if (details == null) {

                    log.warn("MCP get-top-urls: urlId {} in analytics but not found in url-service, skipping", url.urlId());

                    continue;
                }

                sb.append("- %s -> %s (short: %s): %d clicks%n"
                        .formatted(
                                details.slug(),
                                details.originalUrl(),
                                details.shortUrl(),
                                url.clickCount()
                        )
                );
            }

            return sb.toString();

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn(
                        "MCP get-top-urls 4xx userId: {}, limit: {}, status: {}",
                        userId,
                        limit,
                        httpEx.getStatusCode()
                );

                return "Could not retrieve top URLs: %s"
                        .formatted(httpEx.getStatusText());
            }

            throw e;
        }
    }
}