//package com.shortlyai.ai.summary;
//
//import com.shortlyai.ai.operations.AnalyticsOperationsService;
//import com.shortlyai.ai.summary.dto.SummaryResponse;
//import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
//import io.github.resilience4j.retry.annotation.Retry;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.stereotype.Service;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class SummaryService {
//
//    private final ChatClient chatClient;
//
//    // Shared analytics HTTP ops - no direct RestClient here
//    private final AnalyticsOperationsService analyticsOps;
//
//    @CircuitBreaker(name = "analytics-service", fallbackMethod = "summarizeFallback")
//    @Retry(name = "analytics-service")
//    public SummaryResponse summarize(Long urlId, String userId) {
//
//        log.info("Generating summary for urlId: {}, userId: {}", urlId, userId);
//
//        AnalyticsOperationsService.StatsResult stats = analyticsOps.getStats(urlId, userId);
//
//        String prompt = """
//                Write a short, friendly 2-sentence summary of this URL's
//                performance for the dashboard.
//
//                Total clicks: %d
//                """.formatted(stats.totalClicks());
//
//        String text = chatClient.prompt().user(prompt).call().content();
//
//        log.debug("Summary generated for urlId: {}: {}", urlId, text);
//
//        return new SummaryResponse(text);
//    }
//
//    public SummaryResponse summarizeFallback(Long urlId, String userId, Throwable ex) {
//
//        log.error("analytics-service unavailable for summarize, urlId: {}", urlId, ex);
//
//        String prompt = """
//                Write a short, friendly 2-sentence message for a dashboard
//                explaining that performance stats are temporarily unavailable
//                and to check back shortly.
//                """;
//
//        String text = chatClient
//                .prompt()
//                .user(prompt)
//                .call()
//                .content();
//
//        return new SummaryResponse(text);
//    }
//}

package com.shortlyai.ai.summary;

import com.shortlyai.ai.operations.AnalyticsOperationsService;
import com.shortlyai.ai.operations.ResilientAnalyticsOps;
import com.shortlyai.ai.summary.dto.SummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * SummaryService — generates a friendly performance summary for a URL.
 *
 * RESILIENCE NOTE:
 * The original version put @CircuitBreaker on summarize() within this @Service.
 * That pattern works correctly here (unlike @Tool methods) because SummaryService
 * IS invoked through its Spring AOP proxy by SummaryController. The annotation
 * fires as expected.
 *
 * HOWEVER: we now delegate to ResilientAnalyticsOps instead of calling
 * AnalyticsOperationsService directly, for two reasons:
 *   1. Consistency — one CB instance for "analytics-service" across all callers.
 *      If the circuit opens via a tool call, it stays open for summary calls too.
 *   2. Avoids double CB wrapping — if SummaryService had its own @CircuitBreaker
 *      AND called ResilientAnalyticsOps which also has @CircuitBreaker, the inner
 *      CB would catch the exception before the outer one sees it, making outer
 *      fallback unreachable. Single CB at the ops layer is cleaner.
 *
 * The fallback here handles the case where getStats returns null (CB open) rather
 * than catching an exception, since ResilientAnalyticsOps returns null on failure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryService {

    private final ChatClient chatClient;
    private final ResilientAnalyticsOps resilientAnalyticsOps;

    public SummaryResponse summarize(Long urlId, String userId) {
        log.info("Generating summary for urlId={} userId={}", urlId, userId);

        AnalyticsOperationsService.StatsResult stats =
                resilientAnalyticsOps.getStats(urlId, userId);

        // null = CB open or retries exhausted — use graceful degradation prompt
        if (stats == null) {
            log.warn("analytics-service unavailable for summarize urlId={}", urlId);
            return summarizeFallback();
        }

        String prompt = """
                Write a short, friendly 2-sentence summary of this URL's
                performance for the dashboard.

                Total clicks: %d
                """.formatted(stats.totalClicks());

        String text = chatClient.prompt().user(prompt).call().content();

        log.debug("Summary generated for urlId={}: {}", urlId, text);
        return new SummaryResponse(text);
    }

    private SummaryResponse summarizeFallback() {
        String prompt = """
                Write a short, friendly 2-sentence message for a dashboard
                explaining that performance stats are temporarily unavailable
                and to check back shortly.
                """;

        String text = chatClient.prompt().user(prompt).call().content();
        return new SummaryResponse(text);
    }
}