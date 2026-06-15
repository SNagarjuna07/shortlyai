package com.shortlyai.ai.summary;

import com.shortlyai.ai.summary.dto.SummaryResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class SummaryService {

    private final ChatClient chatClient;

    private final RestClient analyticsServiceClient;

    private final String apiPrefix;

    public SummaryService(
            ChatClient chatClient,
            @Qualifier("analyticsServiceClient") RestClient analyticsServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.chatClient = chatClient;
        this.analyticsServiceClient = analyticsServiceClient;
        this.apiPrefix = apiPrefix;
    }

    private record StatsResponse(long urlId, long totalClicks, String message) {}

    @CircuitBreaker(name = "analytics-service", fallbackMethod = "summarizeFallback")
    @Retry(name = "analytics-service")
    public SummaryResponse summarize(Long urlId, String userId) {

        log.info("Generating summary for urlId: {}, userId: {}", urlId, userId);

        StatsResponse stats = analyticsServiceClient.get()
                .uri(apiPrefix + "/analytics/{urlId}", urlId)
                .header("X-User-Id", userId)
                .retrieve()
                .body(StatsResponse.class);

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

        String text = chatClient.prompt().user(prompt).call().content();

        return new SummaryResponse(text);
    }
}