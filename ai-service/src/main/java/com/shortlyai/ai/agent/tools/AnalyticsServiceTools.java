package com.shortlyai.ai.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
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
public class AnalyticsServiceTools {

    private final RestClient analyticsServiceClient;

    private final String apiPrefix;

    public AnalyticsServiceTools(
            @Qualifier("analyticsServiceClient") RestClient analyticsServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.analyticsServiceClient = analyticsServiceClient;
        this.apiPrefix = apiPrefix;
    }

    private record StatsResponse(Long urlId, long totalClicks, String message) {}

    @Tool(description = "Get click analytics for a shortened URL by its urlId")
    public String getUrlStats(
            @ToolParam(description = "numeric ID of the shortened URL") Long urlId,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getUrlStats invoked userId: {}, urlId: {}", userId, urlId);

        StatsResponse stats = analyticsServiceClient.get()
                .uri(apiPrefix + "/analytics/{urlId}", urlId)
                .header("X-User-Id", userId)
                .retrieve()
                .body(StatsResponse.class);

        log.debug("getUrlStats result userId: {}, urlId: {}, totalClicks: {}",
                userId, urlId, stats.totalClicks());

        return "URL %d has %d total clicks (%d in the last hour)"
                .formatted(stats.urlId(), stats.totalClicks(), stats.totalClicks());
    }

    private record TopUrl(Long urlId, long clickCount) {}

    @Tool(description = "Get the top performing URLs by click count for the current user")
    public String getTopUrls(
            @ToolParam(description = "how many top URLs to return") int limit,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getTopUrls invoked userId: {}, limit: {}", userId, limit);

        List<TopUrl> topUrls = analyticsServiceClient.get()
                .uri(apiPrefix + "/analytics/top?limit={limit}", limit)
                .header("X-User-Id", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<TopUrl>>() {} );

        log.debug("getTopUrls result userId: {}, count: {}", userId, topUrls.size());

        StringBuilder sb = new StringBuilder("Top URLs:\n");

        for (TopUrl url : topUrls) {
            sb.append("- urlId ").append(url.urlId())
                    .append(": ").append(url.clickCount())
                    .append(" clicks\n");
        }

        return sb.toString();
    }
}