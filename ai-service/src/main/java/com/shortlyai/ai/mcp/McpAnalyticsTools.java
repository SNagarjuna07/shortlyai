package com.shortlyai.ai.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@Slf4j
public class McpAnalyticsTools {

    private final RestClient analyticsServiceClient;

    private final String apiPrefix;

    public McpAnalyticsTools(
            @Qualifier("analyticsServiceClient") RestClient analyticsServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.analyticsServiceClient = analyticsServiceClient;
        this.apiPrefix = apiPrefix;
    }

    private record StatsResponse(Long urlId, long totalClicks, String message) {}

    @Tool(description = """
            Get total click count for a shortened URL by its numeric urlId.
            Use getUrlDetails first if you only have the slug - it returns the urlId.
            """)
    public String getUrlStats(
            @ToolParam(description = "Numeric urlId (Long) of the shortened URL") Long urlId,
            @ToolParam(description = "Authenticated user UUID") String userId
    ) {

        log.info("MCP getUrlStats userId: {}, urlId: {}", userId, urlId);

        StatsResponse stats = analyticsServiceClient.get()
                .uri(apiPrefix + "/analytics/{urlId}", urlId)
                .header("X-User-Id", userId)
                .retrieve()
                .body(StatsResponse.class);

        return "URL with ID %d has %d total clicks".formatted(stats.urlId(), stats.totalClicks());
    }

    private record TopUrl(Long urlId, long clickCount) {}

    @Tool(description = "Get the user's top performing shortened URLs ranked by click count.")
    public String getTopUrls(
            @ToolParam(description = "How many top URLs to return (e.g. 5)") int limit,
            @ToolParam(description = "Authenticated user UUID") String userId
    ) {

        log.info("MCP getTopUrls userId: {}, limit: {}", userId, limit);

        List<TopUrl> topUrls = analyticsServiceClient.get()
                .uri(apiPrefix + "/analytics/top?limit={limit}", limit)
                .header("X-User-Id", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<TopUrl>>() {});

        StringBuilder sb = new StringBuilder("Top %d URLs:\n".formatted(topUrls.size()));

        for (TopUrl url : topUrls) {
            sb.append("- urlId %d: %d clicks\n".formatted(url.urlId(), url.clickCount()));
        }
        
        return sb.toString();
    }
}