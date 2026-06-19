package com.shortlyai.ai.operations;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientAnalyticsOps {

    private final AnalyticsOperationsService analyticsOps;

    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getStatsFallback")
    @Retry(name = "analytics-service")
    public AnalyticsOperationsService.StatsResult getStats(Long urlId, String userId) {

        log.debug("Fetching URL stats for userId: {} urlId: {}", userId, urlId);

        return analyticsOps.getStats(urlId, userId);
    }

    public AnalyticsOperationsService.StatsResult getStatsFallback(
            Long urlId, String userId, Throwable ex) {

        log.error("analytics-service is not available. Please try after some time");

        return null;
    }

    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getTopUrlsFallback")
    @Retry(name = "analytics-service")
    public List<AnalyticsOperationsService.TopUrlResult> getTopUrls(int limit, String userId) {

        log.debug("Fetching top URLs for userId: {} limit: {}", userId, limit);

        return analyticsOps.getTopUrls(limit, userId);
    }

    public List<AnalyticsOperationsService.TopUrlResult> getTopUrlsFallback(
            int limit, String userId, Throwable ex) {

        log.error("analytics-service is down. Failed to fetch your top URLs. Please try again later", ex);

        return Collections.emptyList();
    }
}