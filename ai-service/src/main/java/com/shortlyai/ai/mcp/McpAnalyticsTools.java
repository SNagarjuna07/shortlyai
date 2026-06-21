package com.shortlyai.ai.mcp;

import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.operations.ResilientAnalyticsOps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.concurrent.CompletionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpAnalyticsTools {

    private final ResilientAnalyticsOps resilientAnalyticsOps;

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
    public String getUrlStats(
            @ToolParam(description = "Numeric urlId (Long) of the shortened URL") Long urlId
    ) {

        String userId = authenticatedUserId();

        log.info("MCP getUrlStats userId: {}, urlId: {}", userId, urlId);

        try {

            AnalyticsOperationsService.StatsResult stats = resilientAnalyticsOps
                    .getStats(urlId, userId)
                    .join();

            if (stats == null) {
                return "Click stats for URL %d temporarily unavailable."
                        .formatted(urlId);
            }

            return "URL with ID %d has %d total clicks"
                    .formatted(stats.urlId(), stats.totalClicks());

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn(
                        "MCP getUrlStats 4xx userId: {}, urlId: {}, status: {}",
                        userId,
                        urlId,
                        httpEx.getStatusCode()
                );

                return "Could not retrieve stats for URL %d: %s"
                        .formatted(urlId, httpEx.getStatusText());
            }

            throw e;
        }
    }

    @Tool(name = "mcp_getTopUrls", description = "Get the top performing shortened URLs ranked by click count.")
    public String getTopUrls(
            @ToolParam(description = "How many top URLs to return (e.g. 5)") int limit
    ) {

        String userId = authenticatedUserId();

        log.info("MCP getTopUrls userId: {}, limit: {}", userId, limit);

        try {

            List<AnalyticsOperationsService.TopUrlResult> topUrls =
                    resilientAnalyticsOps
                            .getTopUrls(limit, userId)
                            .join();

            if (topUrls.isEmpty()) {
                return "No URLs found.";
            }

            StringBuilder sb = new StringBuilder("Top %d URLs:\n".formatted(topUrls.size()));

            for (AnalyticsOperationsService.TopUrlResult url : topUrls) {
                sb.append("- urlId %d: %d clicks\n"
                        .formatted(
                                url.urlId(),
                                url.clickCount()
                        )
                );
            }

            return sb.toString();

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn(
                        "MCP getTopUrls 4xx userId: {}, limit: {}, status: {}",
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