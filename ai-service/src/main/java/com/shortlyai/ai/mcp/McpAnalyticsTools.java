package com.shortlyai.ai.mcp;

import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.operations.ResilientAnalyticsOps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * MCP-facing analytics tools.
 * Thin shell — resilience delegated to ResilientAnalyticsOps.
 * See McpUrlTools for the full design rationale.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpAnalyticsTools {

    private final ResilientAnalyticsOps resilientAnalyticsOps;

    private void validateUserId(String userId) {

        try {

            UUID.fromString(userId);

        } catch (IllegalArgumentException ex) {

            throw new IllegalArgumentException(
                    "Invalid userId - must be a UUID: " + userId);
        }
    }

    @Tool(name = "mcp_getUrlStats", description = """
            Get total click count for a shortened URL by its numeric urlId.
            Use mcp_getUrlDetails first if you only have the slug — it returns the urlId.
            """)
    public String getUrlStats(
            @ToolParam(description = "Numeric urlId (Long) of the shortened URL") Long urlId,
            @ToolParam(description = "Authenticated user UUID") String userId
    ) {

        validateUserId(userId);

        log.info("MCP getUrlStats userId={} urlId={}", userId, urlId);

        AnalyticsOperationsService.StatsResult stats =
                resilientAnalyticsOps.getStats(urlId, userId);

        if (stats == null) {
            return "Click stats for URL %d temporarily unavailable.".formatted(urlId);
        }

        return "URL with ID %d has %d total clicks".formatted(stats.urlId(), stats.totalClicks());
    }

    @Tool(name = "mcp_getTopUrls", description = "Get the user's top performing shortened URLs ranked by click count.")
    public String getTopUrls(
            @ToolParam(description = "How many top URLs to return (e.g. 5)") int limit,
            @ToolParam(description = "Authenticated user UUID") String userId
    ) {

        validateUserId(userId);

        log.info("MCP getTopUrls userId={} limit={}", userId, limit);

        List<AnalyticsOperationsService.TopUrlResult> topUrls =
                resilientAnalyticsOps.getTopUrls(limit, userId);

        if (topUrls.isEmpty()) {
            return "Top URLs temporarily unavailable — analytics-service is down.";
        }

        StringBuilder sb = new StringBuilder("Top %d URLs:\n".formatted(topUrls.size()));

        for (AnalyticsOperationsService.TopUrlResult url : topUrls) {
            sb.append("- urlId %d: %d clicks\n".formatted(url.urlId(), url.clickCount()));
        }

        return sb.toString();
    }
}