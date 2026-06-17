package com.shortlyai.ai.mcp;

import com.shortlyai.ai.operations.AnalyticsOperationsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpAnalyticsTools {

    private final AnalyticsOperationsService analyticsOps;

    private void validateUserId(String userId) {

        try {

            UUID.fromString(userId);

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException("Invalid userId — must be a UUID: " + userId);
        }
    }

    @Tool(name = "mcp_getUrlStats", description = """
            Get total click count for a shortened URL by its numeric urlId.
            Use mcp_getUrlDetails first if you only have the slug - it returns the urlId.
            """)
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getUrlStatsFallback")
    @Retry(name = "analytics-service")
    public String getUrlStats(
            @ToolParam(description = "Numeric urlId (Long) of the shortened URL") Long urlId,
            @ToolParam(description = "Authenticated user UUID") String userId
    ) {

        validateUserId(userId);

        log.info("MCP getUrlStats userId: {}, urlId: {}", userId, urlId);

        AnalyticsOperationsService.StatsResult stats = analyticsOps.getStats(urlId, userId);

        return "URL with ID %d has %d total clicks".formatted(stats.urlId(), stats.totalClicks());
    }

    public String getUrlStatsFallback(Long urlId, String userId, Throwable ex) {

        log.error("analytics-service unavailable for MCP getUrlStats, urlId: {}", urlId, ex);

        return "Click stats for URL %d temporarily unavailable.".formatted(urlId);
    }

    @Tool(name = "mcp_getTopUrls", description = "Get the user's top performing shortened URLs ranked by click count.")
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getTopUrlsFallback")
    @Retry(name = "analytics-service")
    public String getTopUrls(
            @ToolParam(description = "How many top URLs to return (e.g. 5)") int limit,
            @ToolParam(description = "Authenticated user UUID") String userId
    ) {

        validateUserId(userId);

        log.info("MCP getTopUrls userId: {}, limit: {}", userId, limit);

        List<AnalyticsOperationsService.TopUrlResult> topUrls = analyticsOps.getTopUrls(limit, userId);

        StringBuilder sb = new StringBuilder("Top %d URLs:\n".formatted(topUrls.size()));

        for (AnalyticsOperationsService.TopUrlResult url : topUrls) {
            sb.append("- urlId %d: %d clicks\n".formatted(url.urlId(), url.clickCount()));
        }

        return sb.toString();
    }

    public String getTopUrlsFallback(int limit, String userId, Throwable ex) {

        log.error("analytics-service unavailable for MCP getTopUrls, limit: {}", limit, ex);

        return "Top URLs temporarily unavailable - analytics-service is down.";
    }
}