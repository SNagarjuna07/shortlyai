package com.shortlyai.ai.mcp;

import com.shortlyai.ai.operations.AnalyticsOperationsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpAnalyticsTools {

    private final AnalyticsOperationsService analyticsOps;

    // userId from McpKeyFilter-injected context — not from LLM input
    private String authenticatedUserId() {

        String userId = McpUserContext.get();

        if (userId == null) {

            throw new IllegalStateException("No authenticated userId in MCP context - filter misconfigured");
        }

        return userId;
    }

    @Tool(name = "mcp_getUrlStats", description = """
            Get total click count for a shortened URL by its numeric urlId.
            Use mcp_getUrlDetails first if you only have the slug - it returns the urlId.
            """)
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getUrlStatsFallback")
    @Retry(name = "analytics-service")
    public String getUrlStats(
            @ToolParam(description = "Numeric urlId (Long) of the shortened URL") Long urlId
    ) {

        String userId = authenticatedUserId();

        log.info("MCP getUrlStats userId: {}, urlId: {}", userId, urlId);

        try {

            AnalyticsOperationsService.StatsResult stats = analyticsOps.getStats(urlId, userId);

            return "URL with ID %d has %d total clicks".formatted(stats.urlId(), stats.totalClicks());

        } catch (HttpClientErrorException e) {

            // 404 = urlId not found in analytics-service - server healthy, don't trip CB
            log.warn("MCP getUrlStats 4xx userId: {}, urlId: {}, status: {}", userId, urlId, e.getStatusCode());

            return "Could not retrieve stats for URL %d: %s".formatted(urlId, e.getStatusText());
        }
    }

    public String getUrlStatsFallback(Long urlId, Throwable ex) {

        log.error("analytics-service unavailable for MCP getUrlStats, urlId: {}", urlId, ex);

        return "Click stats for URL %d temporarily unavailable.".formatted(urlId);
    }

    @Tool(name = "mcp_getTopUrls", description = "Get the top performing shortened URLs ranked by click count.")
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getTopUrlsFallback")
    @Retry(name = "analytics-service")
    public String getTopUrls(
            @ToolParam(description = "How many top URLs to return (e.g. 5)") int limit
    ) {

        String userId = authenticatedUserId();

        log.info("MCP getTopUrls userId: {}, limit: {}", userId, limit);

        try {

            List<AnalyticsOperationsService.TopUrlResult> topUrls = analyticsOps.getTopUrls(limit, userId);

            if (topUrls.isEmpty()) {
                return "No URLs found.";
            }

            StringBuilder sb = new StringBuilder("Top %d URLs:\n".formatted(topUrls.size()));

            for (AnalyticsOperationsService.TopUrlResult url : topUrls) {
                sb.append("- urlId %d: %d clicks\n".formatted(url.urlId(), url.clickCount()));
            }

            return sb.toString();

        } catch (HttpClientErrorException e) {

            // 4xx from analytics-service - server healthy, don't trip CB
            log.warn("MCP getTopUrls 4xx userId: {}, limit: {}, status: {}", userId, limit, e.getStatusCode());

            return "Could not retrieve top URLs: %s".formatted(e.getStatusText());
        }
    }

    public String getTopUrlsFallback(int limit, Throwable ex) {

        log.error("analytics-service unavailable for MCP getTopUrls, limit: {}", limit, ex);

        return "Top URLs temporarily unavailable - analytics-service is down.";
    }
}