package com.shortlyai.ai.agent.tools;

import com.shortlyai.ai.operations.AnalyticsOperationsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceTools {

    private final AnalyticsOperationsService analyticsOps;

    @Tool(description = "Get click analytics for a shortened URL by its urlId")
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getUrlStatsFallback")
    @Retry(name = "analytics-service")
    public String getUrlStats(
            @ToolParam(description = "numeric ID of the shortened URL") Long urlId,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getUrlStats userId: {}, urlId: {}", userId, urlId);

        AnalyticsOperationsService.StatsResult stats = analyticsOps.getStats(urlId, userId);

        log.debug("getUrlStats urlId: {}, totalClicks: {}", urlId, stats.totalClicks());

        return "URL %d has %d total clicks".formatted(stats.urlId(), stats.totalClicks());
    }

    public String getUrlStatsFallback(Long urlId, ToolContext toolContext, Throwable ex) {

        log.error("analytics-service unavailable for getUrlStats, urlId: {}", urlId, ex);

        return "Click stats for URL %d temporarily unavailable.".formatted(urlId);
    }

    @Tool(description = "Get the top performing URLs by click count for the current user")
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getTopUrlsFallback")
    @Retry(name = "analytics-service")
    public String getTopUrls(
            @ToolParam(description = "how many top URLs to return") int limit,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getTopUrls userId: {}, limit: {}", userId, limit);

        List<AnalyticsOperationsService.TopUrlResult> topUrls = analyticsOps.getTopUrls(limit, userId);

        log.debug("getTopUrls userId: {}, count: {}", userId, topUrls.size());

        StringBuilder sb = new StringBuilder("Top URLs:\n");

        for (AnalyticsOperationsService.TopUrlResult url : topUrls) {
            sb.append("- urlId ").append(url.urlId())
                    .append(": ").append(url.clickCount()).append(" clicks\n");
        }

        return sb.toString();
    }

    public String getTopUrlsFallback(int limit, ToolContext toolContext, Throwable ex) {

        log.error("analytics-service unavailable for getTopUrls, limit: {}", limit, ex);

        return "Top URLs temporarily unavailable - analytics-service is down.";
    }
}