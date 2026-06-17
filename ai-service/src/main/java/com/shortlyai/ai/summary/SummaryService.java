package com.shortlyai.ai.summary;

import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.summary.dto.SummaryResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryService {

    private final ChatClient chatClient;

    // Shared analytics HTTP ops - no direct RestClient here
    private final AnalyticsOperationsService analyticsOps;

    @CircuitBreaker(name = "analytics-service", fallbackMethod = "summarizeFallback")
    @Retry(name = "analytics-service")
    public SummaryResponse summarize(Long urlId, String userId) {

        log.info("Generating summary for urlId: {}, userId: {}", urlId, userId);

        AnalyticsOperationsService.StatsResult stats = analyticsOps.getStats(urlId, userId);

        String prompt = """
                Write a short, friendly 2-sentence summary of this URL's
                performance for the dashboard.
                
                Total clicks: %d
                """.formatted(stats.totalClicks());

        String text = chatClient.prompt().user(prompt).call().content();

        log.debug("Summary generated for urlId: {}: {}", urlId, text);

        return new SummaryResponse(text);
    }

    public SummaryResponse summarizeFallback(Long urlId, String userId, Throwable ex) {

        log.error("analytics-service unavailable for summarize, urlId: {}", urlId, ex);

        String prompt = """
                Write a short, friendly 2-sentence message for a dashboard
                explaining that performance stats are temporarily unavailable
                and to check back shortly.
                """;

        String text = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

        return new SummaryResponse(text);
    }
}